package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.http.MediaType
import com.twitter.util.Future
import java.io.InputStream
import java.util.UUID

final class StagedPackageStorage private(objectStorage: ObjectStorage) {

  import ObjectStorageOps.objectStorageOps

  def read(id: UUID): Future[(MediaType, InputStream)] = {
    // TODO package-add: Test for failure cases
    objectStorage.read(id.toString).map(_.get)
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

}

object StagedPackageStorage {

  def apply(objectStorage: ObjectStorage): StagedPackageStorage = {
    new StagedPackageStorage(objectStorage)
  }

}
