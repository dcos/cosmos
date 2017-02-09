package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.storage.ObjectStorageOps.objectStorageOps
import com.twitter.util.Future

final class ObjectStorageTestOps(val objectStorage: ObjectStorage) extends AnyVal {

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

  def listAllObjects(root: String): Future[List[ObjectStorageItem]] = {
    objectStorage.listAllObjectNames(root).flatMap { names =>
      Future.collect(names.map { name =>
        for {
          Some((mediaType, content)) <- objectStorage.readAsArray(name)
          timeCreated <- objectStorage.getCreationTime(name)
        } yield ObjectStorageItem(name, content, Some(mediaType), timeCreated)
      }).map(_.toList)
    }
  }

  def deleteAll(names: List[String]): Future[Unit] = {
    Future.join(
      names.map(objectStorage.delete)
    )
  }

}

object ObjectStorageTestOps {
  import scala.language.implicitConversions

  implicit def objectStorageTestOps(objectStorage: ObjectStorage): ObjectStorageTestOps = {
    new ObjectStorageTestOps(objectStorage)
  }
}
