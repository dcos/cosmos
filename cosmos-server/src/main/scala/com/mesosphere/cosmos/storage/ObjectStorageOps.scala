package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.storage.ObjectStorage.ListToken
import com.twitter.io.StreamIO
import com.twitter.util.Future
import com.twitter.util.FuturePool
import java.io.ByteArrayInputStream

class ObjectStorageOps(objectStorage: ObjectStorage) {

  import ObjectStorageOps._

  private[this] val pool = FuturePool.interruptibleUnboundedPool

  def readAsArray(name: String): Future[Option[(MediaType, Array[Byte])]] = {
    objectStorage.read(name).flatMap { item =>
      pool { item.map { case (mediaType, readStream) =>
        (mediaType, StreamIO.buffer(readStream).toByteArray)
      }}.ensure(item.foreach(_._2.close()))
    }
  }

  def write(
    name: String,
    content: Array[Byte],
    mediaType: MediaType
  ): Future[Unit] = {
    write(name, content, Some(mediaType))
  }

  def write(
    name: String,
    content: Array[Byte],
    mediaType: Option[MediaType] = None
  ): Future[Unit] = {
    val body = new ByteArrayInputStream(content)
    val contentLength = content.length.toLong
    objectStorage.write(name, body, contentLength, mediaType)
  }

  def writeAll(objectStorageItems: List[ObjectStorageItem]): Future[Unit] = {
    Future.join(
      objectStorageItems.map { objectStorageItem =>
        objectStorage.write(
          objectStorageItem.name,
          objectStorageItem.content,
          objectStorageItem.mediaType
        )
      }
    )
  }

  def deleteAll(names: List[String]): Future[Unit] = {
    Future.join(
      names.map(objectStorage.delete)
    )
  }

  def listAllObjectNames(root: String): Future[List[String]] = {
    objectStorage.listWithoutPaging(root).flatMap { objectList =>
      Future.collect(
        objectList.directories.map(listAllObjectNames)
      ).map { rest =>
        (objectList.objects :: rest.toList).flatten
      }
    }
  }

  def listAllObjects(root: String): Future[List[ObjectStorageItem]] = {
    listAllObjectNames(root).flatMap { names =>
      Future.collect(names.map { name =>
        for {
          Some((mediaType, content)) <- objectStorage.readAsArray(name)
          timeCreated <- objectStorage.getCreationTime(name)
        } yield ObjectStorageItem(name, content, Some(mediaType), timeCreated)
      }).map(_.toList)
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

  private[this] def collectPages(
    firstPage: Future[ObjectStorage.ObjectList]
  ): Future[List[ObjectStorage.ObjectList]] = {
    collectPagesReversed(firstPage.map(List(_))).map(_.reverse)
  }

  private[this] def collectPagesReversed(
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
