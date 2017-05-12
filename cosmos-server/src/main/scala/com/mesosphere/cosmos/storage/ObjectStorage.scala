package com.mesosphere.cosmos.storage

import com.amazonaws.services.s3.AmazonS3Client
import com.mesosphere.cosmos.FileUri
import com.mesosphere.cosmos.ObjectStorageUri
import com.mesosphere.cosmos.S3Uri
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.util.AbsolutePath
import com.netaporter.uri.Uri
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Future
import java.io.InputStream
import java.time.Instant
import scala.language.implicitConversions

/**
 * General interface for storing and retrieving objects
 */
trait ObjectStorage {
  /**
   * Writes an object to the store. If contentType is not provided the default
   * application/octet-stream is used.
   */
  final def write(
    path: AbsolutePath,
    body: InputStream,
    contentLength: Long,
    contentType: Option[MediaType] = None
  ): Future[Unit] = {
    write(
      path,
      body,
      contentLength,
      contentType.getOrElse(MediaTypes.applicationOctetStream)
    )
  }

  /**
   * Writes an object to the store.
   */
  def write(
    path: AbsolutePath,
    body: InputStream,
    contentLength: Long,
    contentType: MediaType
  ): Future[Unit]

  /**
   * Reads an object from the store. If the file doesn't exists, None is returned.
   */
  def read(path: AbsolutePath): Future[Option[(MediaType, InputStream)]]

  /**
   * Deletes the specified object. If attempting to delete an object that does not exist,
   * success is returned.
   */
  def delete(path: AbsolutePath): Future[Unit]

  /**
   * List some objects and directories in the given path. If there are too many objects or
   * directories, the result is truncated and the listToken property is set to Some value.
   */
  def list(path: AbsolutePath): Future[ObjectStorage.ObjectList]

  /**
   * List some objects and directories based on the result of a previous list or listNext call.
   * If there are too many objects or directories, the result is truncated and the listToken
   * property is set to Some value.
   */
  def listNext(token: ObjectStorage.ListToken): Future[ObjectStorage.ObjectList]

  /**
   * Get the URL for an object if the backing store supports it.
   */
  def getUrl(path: AbsolutePath): Option[Uri]

  /**
    * Gets the creation time of the object in the store. Returns None if the object does not
    * exist.
    */
  def getCreationTime(path: AbsolutePath): Future[Option[Instant]]
}

object ObjectStorage {
  /**
   * Opaque type for representing the token to the listNext method.
   */
  trait ListToken

  /**
   * Type returned by both list and listNext methods.
   */
  trait ObjectList {
    val objects: List[AbsolutePath]
    val directories: List[AbsolutePath]
    val listToken: Option[ListToken]
  }

  def fromUri(uri: ObjectStorageUri)(implicit statsReceiver: StatsReceiver): ObjectStorage = {
    uri match {
      case S3Uri(s3Uri) => S3ObjectStorage(new AmazonS3Client(), s3Uri)
      case FileUri(path) => LocalObjectStorage(path)
    }
  }

  implicit def objectStorageOps(objectStorage: ObjectStorage): ObjectStorageOps = {
    new ObjectStorageOps(objectStorage)
  }

}
