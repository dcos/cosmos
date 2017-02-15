package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.util.AbsolutePath
import com.twitter.util.Future
import java.io.InputStream
import java.time.Instant
import java.util.UUID

final class StagedPackageStorage private(objectStorage: ObjectStorage) {

  import StagedPackageStorage._

  def read(id: UUID): Future[Option[(MediaType, InputStream)]] = {
    objectStorage.read(uuidToPath(id))
  }

  def write(content: InputStream, contentLength: Long, mediaType: MediaType): Future[UUID] = {
    val id = UUID.randomUUID()
    objectStorage.write(uuidToPath(id), content, contentLength, mediaType) before
      Future.value(id)
  }

  def list(): Future[List[UUID]] = {
    objectStorage.listWithoutPaging(AbsolutePath.Root).map { objectList =>
      objectList.directories.flatMap(path => path.elements.lastOption.map(UUID.fromString))
    }
  }

  def delete(id: UUID): Future[Unit] = {
    objectStorage.delete(uuidToPath(id))
  }

  def getCreationTime(id: UUID): Future[Option[Instant]] = {
    objectStorage.getCreationTime(uuidToPath(id))
  }

}

object StagedPackageStorage {

  def apply(objectStorage: ObjectStorage): StagedPackageStorage = {
    new StagedPackageStorage(objectStorage)
  }

  def uuidToPath(id: UUID): AbsolutePath = AbsolutePath.Root / id.toString

}
