package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.http.MediaType
import com.twitter.util.Future
import java.io.InputStream
import java.time.Instant
import java.util.UUID

final class StagedPackageStorage private(objectStorage: ObjectStorage) {

  def read(id: UUID): Future[Option[(MediaType, InputStream)]] = {
    objectStorage.read(id.toString)
  }

  def write(content: InputStream, contentLength: Long, mediaType: MediaType): Future[UUID] = {
    val id = UUID.randomUUID()
    objectStorage.write(id.toString, content, contentLength, mediaType) before
      Future.value(id)
  }

  def list(): Future[List[UUID]] = {
    objectStorage.listWithoutPaging("").map { objectList =>
      objectList.directories.map(UUID.fromString)
    }
  }

  def delete(id: UUID): Future[Unit] = {
    objectStorage.delete(id.toString)
  }

  def getCreationTime(id: UUID): Future[Option[Instant]] = {
    objectStorage.getCreationTime(id.toString)
  }

}

object StagedPackageStorage {

  def apply(objectStorage: ObjectStorage): StagedPackageStorage = {
    new StagedPackageStorage(objectStorage)
  }

}
