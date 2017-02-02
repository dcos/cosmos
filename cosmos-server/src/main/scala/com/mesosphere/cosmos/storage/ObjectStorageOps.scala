package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.storage.ObjectStorage.ListToken
import com.twitter.util.Future

class ObjectStorageOps(objectStorage: ObjectStorage) {

  import ObjectStorageOps._

  def listAllObjects(root: String): Future[List[String]] = {
    objectStorage.listWithoutPaging(root).flatMap { objectList =>
      Future.collect(
        objectList.directories.map(listAllObjects)
      ).map { rest =>
        (objectList.objects :: rest.toList).flatten
      }
    }
  }

  def listWithoutPaging(root: String): Future[ObjectStorage.ObjectList] = {
    collectPages(objectStorage.list(root)).map { pages =>
      new ObjectStorage.ObjectList {
        override val listToken: Option[ListToken] = None
        override val objects: List[String] = pages.flatMap(_.objects)
        override val directories: List[String] = pages.flatMap(_.directories)
      }
    }
  }

  private[this] def collectPages
  (
    firstPage: Future[ObjectStorage.ObjectList]
  ): Future[List[ObjectStorage.ObjectList]] = {
    collectPagesReversed(firstPage.map(List(_))).map(_.reverse)
  }


  private[this] def collectPagesReversed
  (
    pages: Future[List[ObjectStorage.ObjectList]]
  ): Future[List[ObjectStorage.ObjectList]] = {
    pages.flatMap { pagesSoFar =>
      val mostRecentPage = pagesSoFar.head
      mostRecentPage.listToken match {
        case Some(token) =>
          val nextPage = objectStorage.listNext(token)
          collectPagesReversed(nextPage.map(_ :: pagesSoFar))
        case None =>
          Future.value(pagesSoFar)
      }
    }
  }

}

object ObjectStorageOps {
  import scala.language.implicitConversions

  implicit def objectStorageOps(objectStorage: ObjectStorage): ObjectStorageOps = {
    new ObjectStorageOps(objectStorage)
  }
}
