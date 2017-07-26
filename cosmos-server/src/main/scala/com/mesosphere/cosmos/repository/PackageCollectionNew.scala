package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.error.PackageNotFound
import com.mesosphere.cosmos.error.VersionNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc.v1.model.SearchResult
import com.mesosphere.universe.v3.model.Version
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.mesosphere.universe.v4.model.PackageDefinition
import com.mesosphere.universe.v4.model.Repository
import com.netaporter.uri.Uri
import com.twitter.util.Future


final class PackageCollectionNew(
  repositoryCache: RepositoryCache
) extends PackageCollection {

  /*
  getbyname
  getbyrelease
  getbyversion


  def getByName(name: String): Future[List[PackageDefinition]] = {
    inst.all().map { x =>
      PackageCollectionNew.getByName(name, PackageCollectionNew.merge(x))
    }
  }
   */
  override def getPackagesByPackageName(
    packageName: String
  )(implicit session: RequestSession): Future[List[PackageDefinition]] = {
    repositoryCache.all().map { repositories =>
      PackageCollectionNew.getPackagesByPackageName(
        packageName, PackageCollectionNew.merge(repositories))
    }
  }

  override def getPackageByPackageVersion(
    packageName: String,
    packageVersion: Option[Version]
  )(implicit session: RequestSession): Future[(PackageDefinition, Uri)] = {
    repositoryCache.all().map { repositories =>
      PackageCollectionNew.getPackagesByPackageVersion(
        packageName, packageVersion, PackageCollectionNew.mergeWithURI(repositories))
    }
  }

  override def search(
    query: Option[String]
  )(implicit session: RequestSession): Future[List[SearchResult]] = ???

  /**
    * Return the versions of packages in this collection that the package with the given `name` and
    * `version` can be upgraded to.
    */
  override def upgradesTo(
    name: String,
    version: Version
  )(implicit session: RequestSession): Future[List[Version]] = ???

  /**
    * Return the versions of packages in this collection that the package
    * `packageDefinition` can be downgrade to.
    */
  override def downgradesTo(
    packageDefinition: PackageDefinition
  )(implicit session: RequestSession): Future[List[Version]] = ???
}

object PackageCollectionNew {

  def mergeWithURI(repositories: List[(Uri, Repository)]): List[(PackageDefinition, Uri)] = {
    repositories.map { case (uri, repository) =>
      repository.packages map ((_, uri))
    }.flatten
  }

  def merge(repositories: List[(Uri, Repository)]): List[PackageDefinition] = {
    repositories.flatMap{ case (_, repository) =>
      repository.packages
    }
  }

  /*
  getbyname
  getbyrelease
  getbyversion
   */

  def getPackagesByPackageName(
    packageName: String,
    packageDefinitions: List[PackageDefinition]
  ): List[PackageDefinition] = {
    val result = packageDefinitions.filter(_.name == packageName)
    if (result.isEmpty) {
      throw PackageNotFound(packageName).exception
    }
    result
  }

  def getPackagesByPackageVersion(
    packageName: String,
    packageVersion: Option[Version],
    packageDefinitions: List[(PackageDefinition, Uri)]
  ): (PackageDefinition, Uri) = {

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

}