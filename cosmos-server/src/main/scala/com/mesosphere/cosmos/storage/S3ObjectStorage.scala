package com.mesosphere.cosmos.storage

import java.io.InputStream

import scala.collection.JavaConverters._
import scala.collection.breakOut

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.netaporter.uri.Uri
import com.twitter.io.Reader
import com.twitter.util.Future
import com.twitter.util.FuturePool

import com.mesosphere.cosmos.http.MediaType


final class S3ObjectStorage(
  client: AmazonS3Client,
  bucket: String,
  path: String
) extends ObjectStorage {
  // We don't need to make it configurable for now.
  private[this] val pool = FuturePool.interruptibleUnboundedPool

  override def write(
    name: String,
    body: InputStream,
    contentLength: Long,
    contentType: Option[MediaType] = None
  ): Future[Unit] = {
    pool {
      val metadata = new ObjectMetadata()
      metadata.setContentLength(contentLength)
      contentType.foreach(value => metadata.setContentType(value.show))

      val putRequest = new PutObjectRequest(bucket, fullPath(name), body, metadata)
        .withCannedAcl(CannedAccessControlList.PublicRead)

      val _ = client.putObject(putRequest)
    }
  }

  override def read(name: String): Future[Reader] = {
    pool {
      val result = client.getObject(bucket, fullPath(name))
      Reader.fromStream(result.getObjectContent())
    }
  }

  override def list(directory: String): Future[ObjectStorage.ObjectList] = {
    pool {
      val listRequest = new ListObjectsRequest()
        .withBucketName(bucket)
        .withPrefix(makeStringDirectory(fullPath(directory)))
        .withDelimiter("/")

      convertListResult(client.listObjects(listRequest))
    }
  }

  override def listNext(
    listToken: ObjectStorage.ListToken
  ): Future[ObjectStorage.ObjectList] = {
    listToken match {
      case S3ObjectStorage.ListToken(token) =>
        pool {
          convertListResult(client.listNextBatchOfObjects(token))
        }
      case _ =>
        Future.exception(
          new IllegalArgumentException("Programming error. Wrong type of ListToken passed to listNext")
        )
    }
  }

  override def getUrl(name: String): Option[Uri] = {
    Some(Uri(client.getUrl(bucket, fullPath(name)).toURI))
  }


  private[this] def convertListResult(
    objectListing: ObjectListing
  ): ObjectStorage.ObjectList = {
    val listToken = if (objectListing.isTruncated) {
      Some(new S3ObjectStorage.ListToken(objectListing))
    } else {
      None
    }

    S3ObjectStorage.ObjectList(
      objectListing.getObjectSummaries.asScala.map(sum => relativePath(sum.getKey))(breakOut),
      objectListing.getCommonPrefixes.asScala.map(relativePath)(breakOut),
      listToken
    )
  }

  private[this] def fullPath(name: String): String = {
    makeStringDirectory(path) + name
  }

  /*
   * This functiona makes sure that the string ends in a `/`. S3 doesn't have the concept of
   * directory but you it can be emulated by adding `/` to the key. This allows you to list
   * objects with a given prefix.
   */
  private[this] def makeStringDirectory(prefix: String): String = {
    if (prefix.endsWith("/")) prefix else prefix + "/"
  }

  private[this] def relativePath(fullPath: String): String = {
    if (fullPath.startsWith(makeStringDirectory(path))) {
      fullPath.drop(makeStringDirectory(path).length)
    } else {
      fullPath
    }
  }
}

object S3ObjectStorage {
  def apply(
    client: AmazonS3Client,
    bucket: String,
    path: String
  ): S3ObjectStorage = {
    new S3ObjectStorage(client, bucket, path)
  }

  case class ListToken(token: ObjectListing) extends ObjectStorage.ListToken
  case class ObjectList(
    objects: List[String],
    directories: List[String],
    listToken: Option[ListToken]
  ) extends ObjectStorage.ObjectList
}
