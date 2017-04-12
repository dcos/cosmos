package com.mesosphere.cosmos.storage

import com.mesosphere.util.AbsolutePath
import com.twitter.util.Future
import scala.language.implicitConversions

final class ObjectStorageTestOps(val objectStorage: ObjectStorage) extends AnyVal {

  def writeAll(objectStorageItems: List[ObjectStorageItem]): Future[Unit] = {
    Future.join(
      objectStorageItems.map { objectStorageItem =>
        objectStorage.write(
          objectStorageItem.path,
          objectStorageItem.content,
          objectStorageItem.mediaType
        )
      }
    )
  }

  def listAllObjectPaths(root: AbsolutePath): Future[List[AbsolutePath]] = {
    objectStorage.listWithoutPaging(root).flatMap { objectList =>
      Future.collect(
        objectList.directories.map(listAllObjectPaths)
      ).map { rest =>
        (objectList.objects :: rest.toList).flatten
      }
    }
  }

  def listAllObjects(root: AbsolutePath): Future[List[ObjectStorageItem]] = {
    listAllObjectPaths(root).flatMap { paths =>
      Future.collect(paths.map { path =>
        for {
          Some((mediaType, content)) <- objectStorage.readAsArray(path)
          timeCreated <- objectStorage.getCreationTime(path)
        } yield ObjectStorageItem(path, content, Some(mediaType), timeCreated)
      }).map(_.toList)
    }
  }

  def deleteAll(paths: List[AbsolutePath]): Future[Unit] = {
    Future.join(paths.map(objectStorage.delete))
  }

}

object ObjectStorageTestOps {
  implicit def objectStorageTestOps(objectStorage: ObjectStorage): ObjectStorageTestOps = {
    new ObjectStorageTestOps(objectStorage)
  }
}
