package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.error.PackageNotFound
import com.mesosphere.cosmos.error.VersionNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import com.twitter.util.Future
import java.util.regex.Pattern
import scala.util.matching.Regex

final class PackageCollection(repositoryCache: RepositoryCache) {

  def getPackagesByPackageName(
    packageName: String
  )(implicit session: RequestSession): Future[List[universe.v4.model.PackageDefinition]] = {
    repositoryCache.all().map { repositories =>
      PackageCollection.getPackagesByPackageName(
        packageName,
        PackageCollection.merge(repositories)
      )
    }
  }

  def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[universe.v3.model.Version]
  )(implicit session: RequestSession): Future[(universe.v4.model.PackageDefinition, Uri)] = {
    repositoryCache.all().map { repositories =>
      PackageCollection.getPackagesByPackageVersion(
        packageName,
        packageVersion,
        PackageCollection.mergeWithURI(repositories)
      )
    }
  }

  def search(
    query: Option[String]
  )(implicit session: RequestSession): Future[List[rpc.v1.model.SearchResult]] = {
    repositoryCache.all().map { repositories =>
      PackageCollection.search(
        query,
        PackageCollection.merge(repositories)
      )
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
      PackageCollection.upgradesTo(
        name,
        version,
        PackageCollection.merge(repositories)
      )
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
      PackageCollection.downgradesTo(
        packageDefinition,
        PackageCollection.merge(repositories)
      )
    }
  }
}

object PackageCollection {

  /**
    * @param repositories
    * @return
    *         This method takes in a List made up of tuples of type (Uri, Repository). The method
    *         then sorts the tuples by expanding the Repository in to List[PackageDefintion] and
    *         then sorting it using the following criteria:
    *         - Remove all the tuples that are non unique on their name + version combination with
    *           lowest index remaining part of the list and highest index being removed
    *         - Sort based on their name (a to z)
    *           - Then sort based on their index (low to high)
    *           - Then sort based on their releaseVersion (high to low)
    */
  private def mergeWithURI(
    repositories: List[(universe.v4.model.Repository, Uri)]
  ) : List[(universe.v4.model.PackageDefinition, Uri)] = {

    repositories.zipWithIndex.flatMap { case ((repository, uri), index) =>
      repository.packages.map { packageDefinition =>
        ((packageDefinition, uri), index)
      }
    }
      .groupBy { case (((packageDefinition, _), _)) =>
        packageDefinition.name + packageDefinition.version
      }
      .map(_._2.head)
      .toList
      .sortWith { case (((pkgDef1, _), index1), ((pkgDef2, _), index2)) =>
        if (pkgDef1.name == pkgDef2.name) {
          if (index1 == index2) {
            pkgDef1.releaseVersion.value > pkgDef2.releaseVersion.value
          }
          else {
            index1 < index2
          }
        }
        else {
          pkgDef1.name < pkgDef2.name
        }
      }
      .map { case (packageDefinition, _) =>
        packageDefinition
      }
  }

  private def merge(
    repositories: List[(universe.v4.model.Repository, Uri)]
  ) : List[universe.v4.model.PackageDefinition] = {
    mergeWithURI(repositories).map { case (packageDefinition, _) =>
      packageDefinition
    }
  }


  private def getPackagesByPackageName(
    packageName: String,
    packageDefinitions: List[universe.v4.model.PackageDefinition]
  ): List[universe.v4.model.PackageDefinition] = {
    val result = packageDefinitions.filter(_.name == packageName)
    if (result.isEmpty) {
      throw PackageNotFound(packageName).exception
    }
    result
  }

  private def getPackagesByPackageVersion(
    packageName: String,
    packageVersion: Option[universe.v3.model.Version],
    packageDefinitions: List[(universe.v4.model.PackageDefinition, Uri)]
  ): (universe.v4.model.PackageDefinition, Uri) = {

    val ns = packageDefinitions.filter { case (packageDefinition, _) =>
      packageDefinition.name == packageName
    }

    val vs = packageVersion match {
      case Some(ver) => ns.find { case (packageDefinition, _) =>
        packageDefinition.version == ver
      }
      case _ => ns.headOption
    }

    (packageVersion, ns, vs) match {
      case (Some(ver), _ :: _ , None) => throw VersionNotFound(packageName, ver).exception
      case (_, _, None) => throw PackageNotFound(packageName).exception
      case (_, _, Some(pkg)) => pkg
    }
  }

  private def search(
    query: Option[String],
    packageDefinitions: List[universe.v4.model.PackageDefinition]
  ): List[rpc.v1.model.SearchResult] = {
    val predicate =
      query.map { value =>
        if (value.contains("*")) {
          searchRegex(createRegex(value))
        } else {
          searchString(value.toLowerCase())
        }
      } getOrElse { (_: rpc.v1.model.SearchResult) =>
        true
      }

      val searchResults = packageDefinitions.foldLeft(Map.empty[String, rpc.v1.model.SearchResult]) {
        (state, pkg) =>
          val searchResult = state
            .getOrElse(pkg.name, rpc.v1.model.SearchResult(
              pkg.name,
              pkg.version,
              Map((pkg.version, pkg.releaseVersion)),
              pkg.description,
              pkg.framework.getOrElse(false),
              pkg.tags,
              pkg.selected,
              pkg.images
            ))

          val releaseVersion = searchResult.versions.get(pkg.version).map { releaseVersion =>
            import universe.v3.model.ReleaseVersion
            implicitly[Ordering[ReleaseVersion]].max(releaseVersion, pkg.releaseVersion)
          } getOrElse {
            pkg.releaseVersion
          }

          val newSearchResult = searchResult.copy(
            versions=searchResult.versions + ((pkg.version, releaseVersion))
          )
          state + ((pkg.name, newSearchResult))
      }.toList
        .map { case (_, searchResult) => searchResult }
        .filter(predicate)

    searchResults.groupBy {
        case searchResult => searchResult.name
      }.map {
        case (name, list) => list.head
      }.toList
  }

  def upgradesTo(
    name: String,
    version: universe.v3.model.Version,
    packageDefinitions: List[universe.v4.model.PackageDefinition]
  ): List[universe.v3.model.Version] = {
    packageDefinitions.collect {
      case packageDefinition
        if name == packageDefinition.name && packageDefinition.canUpgradeFrom(version) =>
        packageDefinition.version
    }
  }

  def downgradesTo(
    pkgDefinition: universe.v4.model.PackageDefinition,
    packageDefinitions: List[universe.v4.model.PackageDefinition]
  ): List[universe.v3.model.Version] = {
    packageDefinitions.collect {
      case packageDefinition
        if pkgDefinition.name == packageDefinition.name &&
        pkgDefinition.canDowngradeTo(packageDefinition.version) =>
        packageDefinition.version
    }
  }

  def createRegex(query: String): Regex = {
    s"""^${safePattern(query)}$$""".r
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
}
