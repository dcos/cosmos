package com.mesosphere.cosmos

import cats.syntax.apply._
import cats.instances.option._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.util.Future

package object handler {

  type PackageWithSource = (universe.v4.model.PackageDefinition, Uri)

  def getPackageWithSourceOrThrow(
      packageCollection: PackageCollection,
      marathonApp: MarathonApp
  )(
      implicit session: RequestSession
  ): Future[PackageWithSource] = {
    getPackageWithSource(packageCollection, marathonApp).map(_.getOrElse {
      throw new IllegalStateException(
        "Unable to retrieve the old package definition"
      )
    })
  }

  def getPackageWithSource(
      packageCollection: PackageCollection,
      marathonApp: MarathonApp
  )(
      implicit session: RequestSession
  ): Future[Option[PackageWithSource]] = {
    orElse(Future.value(getStoredPackageWithSource(marathonApp)))(
      lookupPackageWithSource(packageCollection, marathonApp)
    )
  }

  def traverse[A, B](a: Option[A])(fun: A => Future[B]): Future[Option[B]] = {
    a.map(fun) match {
      case None    => Future.value(None)
      case Some(v) => v.map(Some(_))
    }
  }

  private def getStoredPackageWithSource(
      marathonApp: MarathonApp
  ): Option[PackageWithSource] = {
    (
      marathonApp.packageDefinition,
      marathonApp.packageRepository.map(_.uri)
    ).tupled
  }

  private def lookupPackageWithSource(
      packageCollection: PackageCollection,
      marathonApp: MarathonApp
  )(
      implicit session: RequestSession
  ): Future[Option[PackageWithSource]] = {
    traverse(getPackageCoordinate(marathonApp)) {
      case (name, version) =>
        packageCollection.getPackageByPackageVersion(name, Some(version))
    }
  }

  private def getPackageCoordinate(
      marathonApp: MarathonApp
  ): Option[(String, universe.v3.model.Version)] = {
    (
      marathonApp.packageName,
      marathonApp.packageVersion
    ).tupled
  }

  private def orElse[A](
      f1: Future[Option[A]]
  )(
      f2: => Future[Option[A]]
  ): Future[Option[A]] = {
    f1.flatMap {
      case r: Some[A] => Future.value(r)
      case None       => f2
    }
  }
}
