package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.util.AbsolutePath
import com.twitter.io.StreamIO
import com.twitter.util.Future
import com.twitter.util.FuturePool
import java.io.ByteArrayInputStream

final class ObjectStorageOps(val objectStorage: ObjectStorage) extends AnyVal {

  def readAsArray(path: AbsolutePath): Future[Option[(MediaType, Array[Byte])]] = {
    objectStorage.read(path).flatMap { item =>
      FuturePool.interruptibleUnboundedPool { item.map { case (mediaType, readStream) =>
        (mediaType, StreamIO.buffer(readStream).toByteArray)
      }}.ensure(item.foreach { case (_ , inputStream) => inputStream.close() })
    }
  }

  def write(
    path: AbsolutePath,
    content: Array[Byte],
    mediaType: MediaType
  ): Future[Unit] = {
    write(path, content, Some(mediaType))
  }

  def write(
    path: AbsolutePath,
    content: Array[Byte],
    mediaType: Option[MediaType] = None
  ): Future[Unit] = {
    val body = new ByteArrayInputStream(content)
    val contentLength = content.length.toLong
    objectStorage.write(path, body, contentLength, mediaType)
  }

  def listWithoutPaging(root: AbsolutePath): Future[ObjectStorageOps.ObjectStrictList] = {
    collectPages(objectStorage.list(root)).map { pages =>
      ObjectStorageOps.ObjectStrictList(
        objects = pages.flatMap(_.objects),
        directories = pages.flatMap(_.directories)
      )
    }
  }

  private[this] def collectPages(
    firstPage: Future[ObjectStorage.ObjectList]
  ): Future[List[ObjectStorage.ObjectList]] = {

    // Requires pages.map(_.nonEmpty) == Future.value(true)
    def collectPagesReversed(
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

    collectPagesReversed(firstPage.map(List(_))).map(_.reverse)
  }

}

object ObjectStorageOps {
  case class ObjectStrictList(objects: List[AbsolutePath], directories: List[AbsolutePath])
}
