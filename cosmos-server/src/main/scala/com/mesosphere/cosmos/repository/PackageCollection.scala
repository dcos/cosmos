package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.error.PackageNotFound
import com.mesosphere.cosmos.error.VersionNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.model.SearchResult
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.mesosphere.universe.v4.model.PackageDefinition
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

  /**
   *
   * The merge should remove all the packages that has same name+version value (with lowest index
   * value staying) and then should sort the rest initially by name and then index and then releaseVersion
   *
   * The sorting should use foldLeft as we need to iterate from left to right to preserve the order.
   * Elements are appended at the end of Sequence and thus Vector is a better choice than List.
   */
  private def mergeWithURI(
    repositories: List[(universe.v4.model.Repository, Uri)]
  ) : List[(universe.v4.model.PackageDefinition, Uri)] = {
    val (_, uniquePackageDefinitions) = repositories
      .zipWithIndex
      .flatMap { case ((repository, uri), index) =>
        repository.packages.map { packageDefinition =>
          ((packageDefinition, uri), index)
        }
      }
      .foldLeft(
        (
          Set.empty[rpc.v1.model.PackageCoordinate],
          Vector.empty[((universe.v4.model.PackageDefinition, Uri), Int)]
        )
      ) { (state, current) =>
        val (uniquePackageCoordinate, uniquePackages) = state
        val ((packageDefinition, _), _) = current
        val packageCoordinate = packageDefinition.packageCoordinate
        if (uniquePackageCoordinate.contains(packageCoordinate)) {
          state
        } else {
          (
            uniquePackageCoordinate + packageCoordinate,
            uniquePackages :+ current
          )
        }
      }

    val packageOrdering = Ordering.Tuple2(
      implicitly(Ordering[(String, Int)]),
      implicitly(Ordering[universe.v3.model.ReleaseVersion].reverse)
    ).on { tuple : ((universe.v4.model.PackageDefinition, Uri), Int) =>
      val ((pkgDef, _), index) = tuple
      ((pkgDef.name, index), pkgDef.releaseVersion)
    }
    val (result, _) = uniquePackageDefinitions.sorted(packageOrdering).unzip
    result.toList
  }

  def createRegex(query: String): Regex = {
    s"""^${safePattern(query)}$$""".r
  }

  private def getPredicate(query: Option[String]) : SearchResult => Boolean = {
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

  private def singleResult(pkg: PackageDefinition): rpc.v1.model.SearchResult = {
    rpc.v1.model.SearchResult(
      pkg.name,
      pkg.version,
      Map((pkg.version, pkg.releaseVersion)),
      pkg.description,
      pkg.framework.getOrElse(false),
      pkg.tags,
      pkg.selected,
      pkg.images
    )
  }
}
