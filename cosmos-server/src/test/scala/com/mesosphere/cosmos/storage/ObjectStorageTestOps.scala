package com.mesosphere.cosmos.storage

import com.twitter.util.Future
import scala.language.implicitConversions

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

  def deleteAll(names: List[String]): Future[Unit] = {
    Future.join(
      names.map(objectStorage.delete)
    )
  }

}

object ObjectStorageTestOps {
  implicit def objectStorageTestOps(objectStorage: ObjectStorage): ObjectStorageTestOps = {
    new ObjectStorageTestOps(objectStorage)
  }
}
