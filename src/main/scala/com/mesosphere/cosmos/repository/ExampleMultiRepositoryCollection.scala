package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.{PackageCache, PackageNotFound}
import com.mesosphere.universe.{ReleaseVersion, PackageDetailsVersion, PackageFiles, UniverseIndexEntry}
import com.netaporter.uri.Uri
import com.twitter.util.Future

/** Manages a sequence of package collections, where package resolution proceeds in sequence order.
  *
  * This is an example of a concrete class that could be used by Cosmos to perform endpoint
  * operations. It supports both package retrieval operations (from [[PackageCollection]]) and
  * package source operations (from [[RepositoryCollection]]). In our actual implementation, it may
  * be more maintainable to separate this into two classes.
  *
  * '''EXAMPLE CODE -- DO NOT USE'''
  */
private final class ExampleMultiRepositoryCollection
  extends PackageCollection with RepositoryCollection {

  /** Higher priority repositories are at smaller indices. */
  private[this] var repositoryOrdering: List[PackageCollection with Repository] = Nil

  def getPackageByPackageVersion(packageName: String, packageVersion: Option[PackageDetailsVersion]): Future[PackageFiles] = {
    findFirst(repositoryOrdering, Future.exception(PackageNotFound(packageName))) { subCollection =>
      subCollection.getPackageByPackageVersion(packageName, packageVersion)
    }
  }

  def getPackageByReleaseVersion(packageName: String, releaseVersion: ReleaseVersion): Future[PackageFiles] = {
    findFirst(repositoryOrdering, Future.exception(PackageNotFound(packageName))) { subCollection =>
      subCollection.getPackageByReleaseVersion(packageName, releaseVersion)
    }
  }

  def getPackageInfo(packageName: String): Future[UniverseIndexEntry] = {
    findFirst(repositoryOrdering, Future.exception(PackageNotFound(packageName))) { subCollection =>
      subCollection.getPackageInfo(packageName)
    }
  }

  private[this] def findFirst[A, B](items: List[A], ifNotFound: => Future[B])(
    f: A => Future[B]
  ): Future[B] = {
    // TODO: Can this be done without explicit recursion while only executing the Futures as needed?
    items match {
      case a :: as => f(a).rescue { case PackageNotFound(_) => findFirst(as, ifNotFound)(f) }
      case _ => ifNotFound
    }
  }

  def search(query: Option[String]): Future[Seq[UniverseIndexEntry]] = {
    Future.collect(repositoryOrdering.map(_.search(query))).map(_.flatten)
  }

  def list: Future[Seq[Repository]] = Future.value(repositoryOrdering)

  def add(name: String, source: Uri, index: Int): Future[Unit] = {
    Future.value {
      // TODO: Check for uniqueness of name and source
      val (front, back) = repositoryOrdering.splitAt(index)
      val packageCollection = new ExampleLocalPackageCollection(name, source, PackageCache.empty)
      repositoryOrdering = front ++ (packageCollection :: back)
    }
  }

  def deleteByName(name: String): Future[Unit] = {
    Future.value {
      repositoryOrdering = repositoryOrdering.filterNot(_.name == name)
    }
  }

  def deleteBySource(source: Uri): Future[Unit] = {
    Future.value {
      repositoryOrdering = repositoryOrdering.filterNot(_.source == source)
    }
  }

}
