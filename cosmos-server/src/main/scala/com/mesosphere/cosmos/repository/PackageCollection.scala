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

  def getPackagesByPackageName(
    packageName: String,
    packageDefinitions: List[universe.v4.model.PackageDefinition]
  ): List[universe.v4.model.PackageDefinition] = {
    val result = packageDefinitions.filter(_.name == packageName)
    if (result.isEmpty) {
      throw PackageNotFound(packageName).exception
    }
    result
  }

  def getPackagesByPackageVersion(
    packageName: String,
    packageVersion: Option[universe.v3.model.Version],
    packageDefinitions: List[(universe.v4.model.PackageDefinition, Uri)]
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
    query: Option[String],
    packageDefinitions: List[PackageDefinition]
  ): List[rpc.v1.model.SearchResult] = {
    val predicate = getPredicate(query)

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
    }
      .toList
      .map { case (_, searchResult) => searchResult }
      .filter(predicate)

    searchResults
      .groupBy {
        case searchResult => searchResult.name
      }
      .map {
        case (_, list) => list.head
      }
      .toList
      .sortBy(p => (!p.selected.getOrElse(false), p.name))
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

  def merge(
    repositories: List[(universe.v4.model.Repository, Uri)]
  ) : List[universe.v4.model.PackageDefinition] = {
    mergeWithURI(repositories).map { case (packageDefinition, _) =>
      packageDefinition
    }
  }

  def createRegex(query: String): Regex = {
    s"""^${safePattern(query)}$$""".r
  }

  val pkgDefTupleOrdering = new Ordering[((universe.v4.model.PackageDefinition, Uri), Int)] {
    override def compare(
      a: ((universe.v4.model.PackageDefinition, Uri), Int),
      b: ((universe.v4.model.PackageDefinition, Uri), Int)
    ): Int = {
      val ((pkgDef1, _), index1) = a
      val ((pkgDef2, _), index2) = b

      val orderName = pkgDef1.name.compare(pkgDef2.name)
      val orderIndex = index1.compare(index2)

      if (orderName != 0) {
        orderName
      } else if (orderIndex != 0) {
        orderIndex
      } else {
        pkgDef2.releaseVersion.value.compare(pkgDef1.releaseVersion.value)
      }
    }
  }

  /**
  *
  * @param repositories Takes in a List of tuples of type (Repository, Uri).
  * @return A List[(PackageDefinition, Uri)] sorted with below criteria:
  *         The method sorts the tuples by expanding the Repository in to List[PackageDefinition]
  *         and then sorting it using the following criteria (in this exact order):
  *         - Remove all the tuples that are non unique on their name + version combination with
  *           the criteria that entry with the lowest index value stays
  *         - Sort based on their name (a to z)
  *         - Sort based on their index (low to high)
  *         - Sort based on their releaseVersion (high to low)
  *
  * The sorting has to be a foldLeft as we need to iterate from left to right to preserve the order.
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
      .foldLeft((Set.empty[rpc.v1.model.PackageCoordinate],
        Vector.empty[((universe.v4.model.PackageDefinition, Uri), Int)])) { (state, current) =>
        val (uniquePackageCoordinate, uniquePackages) = state
        val ((packageDefinition, _), _) = current
        val packageCoordinate = rpc.v1.model.PackageCoordinate(
          packageDefinition.name,
          packageDefinition.version
        )
        if (uniquePackageCoordinate.contains(packageCoordinate)) {
          state
        } else {
          (
            uniquePackageCoordinate + packageCoordinate,
            uniquePackages :+ current
          )
        }
      }
    val (result, _) = uniquePackageDefinitions.sorted(pkgDefTupleOrdering).unzip
    result.toList
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
}
