package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.error.PackageNotFound
import com.mesosphere.cosmos.error.VersionNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc
import com.mesosphere.http.OriginHostScheme
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.util.Future
import java.util.regex.Pattern
import scala.math.Ordering.Implicits.infixOrderingOps
import scala.util.matching.Regex

final class PackageCollection(repositoryCache: RepositoryCache) {

  def getPackagesByPackageName(
    packageName: String
  )(implicit session: RequestSession): Future[List[universe.v4.model.PackageDefinition]] = {
    repositoryCache.all().map { repositories =>
      PackageCollection.getPackagesByPackageName(
        PackageCollection.merge(repositories),
        packageName
      )
    }
  }

  def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[universe.v3.model.Version]
  )(implicit session: RequestSession): Future[(universe.v4.model.PackageDefinition, Uri)] = {
    repositoryCache.all().map { repositories =>
      PackageCollection.getPackagesByPackageVersion(
        PackageCollection.mergeWithURI(repositories),
        packageName,
        packageVersion
      )
    }
  }

  def search(
    query: Option[String]
  )(implicit session: RequestSession): Future[List[rpc.v1.model.SearchResult]] = {
    repositoryCache.all().map { repositories =>
      implicit val originInfo = session.originInfo
      PackageCollection.search(PackageCollection.merge(repositories), query)
    }
  }

  /**
   * Return the versions of packages in this collection that the package with the given `name` and
   * `version` can be upgraded to.
   */
  def upgradesTo(
    name: String,
    version: universe.v3.model.Version
  )(implicit session: RequestSession): Future[List[universe.v3.model.Version]] = {
    repositoryCache.all().map { repositories =>
      PackageCollection.upgradesTo(PackageCollection.merge(repositories), name, version)
    }
  }

  /**
   * Return the versions of packages in this collection that the package
   * `packageDefinition` can be downgrade to.
   */
  def downgradesTo(
    packageDefinition: universe.v4.model.PackageDefinition
  )(implicit session: RequestSession): Future[List[universe.v3.model.Version]] = {
    repositoryCache.all().map { repositories =>
      PackageCollection.downgradesTo(PackageCollection.merge(repositories), packageDefinition)
    }
  }

  def allUrls()(implicit session: RequestSession): Future[Set[String]] = {
    repositoryCache.all().map(PackageCollection.allUrls)
  }
}

object PackageCollection {

  def getPackagesByPackageName(
    packageDefinitions: List[universe.v4.model.PackageDefinition],
    packageName: String
  ): List[universe.v4.model.PackageDefinition] = {
    val result = packageDefinitions.filter(_.name == packageName)
    if (result.isEmpty) {
      throw PackageNotFound(packageName).exception
    }
    result
  }

  def getPackagesByPackageVersion(
    packageDefinitions: List[(universe.v4.model.PackageDefinition, Uri)],
    packageName: String,
    packageVersion: Option[universe.v3.model.Version]
  ): (universe.v4.model.PackageDefinition, Uri) = {
    val packagesMatchingName = packageDefinitions.filter { case (packageDefinition, _) =>
      packageDefinition.name == packageName
    }

    val packagesMatchingVersion = packageVersion match {
      case Some(ver) => packagesMatchingName.find { case (packageDefinition, _) =>
        packageDefinition.version == ver
      }
      case _ => packagesMatchingName.headOption
    }

    (packageVersion, packagesMatchingName, packagesMatchingVersion) match {
      case (Some(ver), _ :: _ , None) => throw VersionNotFound(packageName, ver).exception
      case (_, _, None) => throw PackageNotFound(packageName).exception
      case (_, _, Some(pkg)) => pkg
    }
  }

  def search(
    packageDefinitions: List[universe.v4.model.PackageDefinition],
    query: Option[String]
  )(
    implicit originInfo : OriginHostScheme
  ): List[rpc.v1.model.SearchResult] = {
    val predicate = getPredicate(query)

    packageDefinitions.foldLeft(Map.empty[String, rpc.v1.model.SearchResult]) {
      (state, pkg) =>
        val searchResult = state.getOrElse(pkg.name, singleResult(pkg))

        val releaseVersion = searchResult.versions
          .getOrElse(pkg.version, pkg.releaseVersion)
          .max(pkg.releaseVersion)

        val newSearchResult = searchResult.copy(
          versions = searchResult.versions + ((pkg.version, releaseVersion))
        )

        state + ((pkg.name, newSearchResult))
    }
      .values
      .filter(predicate)
      .toList
      .sortBy(p => (!p.selected.getOrElse(false), p.name))
  }

  def upgradesTo(
    packageDefinitions: List[universe.v4.model.PackageDefinition],
    name: String,
    version: universe.v3.model.Version
  ): List[universe.v3.model.Version] = {
    packageDefinitions.collect {
      case current
        if name == current.name && current.canUpgradeFrom(version) =>
        current.version
    }
  }

  def downgradesTo(
    packageDefinitions: List[universe.v4.model.PackageDefinition],
    packageDefinition: universe.v4.model.PackageDefinition
  ): List[universe.v3.model.Version] = {
    packageDefinitions.collect {
      case current
        if packageDefinition.name == current.name &&
          packageDefinition.canDowngradeTo(current.version) =>
        current.version
    }
  }

  def merge(
    repositories: List[(universe.v4.model.Repository, Uri)]
  ) : List[universe.v4.model.PackageDefinition] = {
    mergeWithURI(repositories).map { case (packageDefinition, _) =>
      packageDefinition
    }
  }

  def allUrls(repositories: List[(universe.v4.model.Repository, Uri)]): Set[String] = {
    repositories.flatMap { case (repo, _) =>
      repo.packages.flatMap {
        case v2: universe.v3.model.V2Package =>
          v2.resource.map(r =>
            r.assets.map(assetsSet) ++ r.images.map(imagesSet))
        case v3: universe.v3.model.V3Package =>
          v3.resource.map(r =>
            r.assets.map(assetsSet) ++ r.images.map(imagesSet) ++ r.cli.map(cliSet))
        case v4: universe.v4.model.V4Package =>
          v4.resource.map(r =>
            r.assets.map(assetsSet) ++ r.images.map(imagesSet) ++ r.cli.map(cliSet))
        case v5: universe.v5.model.V5Package =>
          v5.resource.map(r =>
            r.assets.map(assetsSet) ++ r.images.map(imagesSet) ++ r.cli.map(cliSet))
      }.flatten
    }.flatten.toSet
  }

  /**
   * The merge should remove all the packages that has same name+version value (with lowest index
   * value staying) and then should sort the rest initially by name and then index and then releaseVersion
   */
  private def mergeWithURI(
    repositories: List[(universe.v4.model.Repository, Uri)]
  ) : List[(universe.v4.model.PackageDefinition, Uri)] = {

    val packageOrdering = Ordering.Tuple2(
      Ordering[(String, Int)],
      Ordering[universe.v3.model.ReleaseVersion].reverse
    ).on { tuple : ((universe.v4.model.PackageDefinition, Uri), Int) =>
      val ((pkgDef, _), index) = tuple
      ((pkgDef.name, index), pkgDef.releaseVersion)
    }

    repositories
      .zipWithIndex
      .flatMap { case ((repository, uri), index) =>
        repository.packages.map { packageDefinition =>
          ((packageDefinition, uri), index)
        }
      }
      .groupBy { case ((packageDefinition, _), _) => packageDefinition.packageCoordinate }
      .values
      .map(_.min(packageOrdering))
      .toList
      .sorted(packageOrdering)
      .unzip
      ._1
  }

  def createRegex(query: String): Regex = {
    s"""^${safePattern(query)}$$""".r
  }

  private def getPredicate(query: Option[String]) : rpc.v1.model.SearchResult => Boolean = {
    query.map { value =>
      if (value.contains("*")) {
        searchRegex(createRegex(value))
      } else {
        searchString(value.toLowerCase())
      }
    } getOrElse { (_: rpc.v1.model.SearchResult) =>
      true
    }
  }

  private[this] def searchRegex(
    regex: Regex
  ): rpc.v1.model.SearchResult => Boolean = { pkg =>
    regex.findFirstIn(pkg.name).isDefined ||
      regex.findFirstIn(pkg.description).isDefined ||
      pkg.tags.exists(tag => regex.findFirstIn(tag.value).isDefined)
  }

  private[this] def searchString(
    query: String
  ): rpc.v1.model.SearchResult => Boolean = { pkg =>
    pkg.name.toLowerCase().contains(query) ||
      pkg.description.toLowerCase().contains(query) ||
      pkg.tags.exists(tag => tag.value.toLowerCase().contains(query))
  }

  private[this] def safePattern(query: String): String = {
    query.split("\\*", -1).map{
      case "" => ""
      case v => Pattern.quote(v)
    }.mkString(".*")
  }

  private[this] def singleResult(
    pkg: universe.v4.model.PackageDefinition
  )(
    implicit originInfo : OriginHostScheme
  ): rpc.v1.model.SearchResult = {
    rpc.v1.model.SearchResult(
      pkg.name,
      pkg.version,
      Map((pkg.version, pkg.releaseVersion)),
      pkg.description,
      pkg.framework.getOrElse(false),
      pkg.tags,
      pkg.selected,
      pkg.rewrite(rewriteUrlWithProxyInfo(originInfo), identity).images
    )
  }

  private[this] def assetsSet(assets : universe.v3.model.Assets) : Set[String] = {
    assets.uris.getOrElse(Map.empty).values.toSet
  }

  private[this] def imagesSet(images : universe.v3.model.Images) : Set[String] = {
    images.screenshots.getOrElse(List()).toSet ++
      images.iconSmall ++
      images.iconMedium ++
      images.iconLarge
  }

  private[this] def cliSet(cli : universe.v3.model.Cli) : Set[String] = {
    cli.binaries match {
      case Some(binaries) =>
          binaries.linux.map(_.`x86-64`.url).toSet ++
          binaries.windows.map(_.`x86-64`.url) ++
          binaries.darwin.map(_.`x86-64`.url)
      case None => Set()
    }
  }
}
