package com.mesosphere.cosmos.storage

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.GetObjectMetadataRequest
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.mesosphere.cosmos.http.MediaType
import com.netaporter.uri.Uri
import com.twitter.finagle.stats.Stat
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Future
import com.twitter.util.FuturePool
import java.io.InputStream
import java.time.Instant

import scala.collection.JavaConverters._
import scala.collection.breakOut

final class S3ObjectStorage(
  client: AmazonS3Client,
  s3Uri: AmazonS3URI
)(
  implicit statsReceiver: StatsReceiver
) extends ObjectStorage {
  // We don't need to make it configurable for now.
  private[this] val pool = FuturePool.interruptibleUnboundedPool

  private[this] val bucket = s3Uri.getBucket
  private[this] val path = s3Uri.getURI.getPath

  private[this] val stats = statsReceiver.scope(s"S3ObjectStorage($bucket, $path)")

  override def write(
    name: String,
    body: InputStream,
    contentLength: Long,
    contentType: MediaType
  ): Future[Unit] = {
    Stat.timeFuture(stats.stat("write")) {
      pool {
        val metadata = new ObjectMetadata()
        metadata.setContentLength(contentLength)
        metadata.setContentType(contentType.show)

        val putRequest = new PutObjectRequest(bucket, fullPath(name), body, metadata)
          .withCannedAcl(CannedAccessControlList.PublicRead)

          val _ = client.putObject(putRequest)
      }
    }
  }

  override def read(name: String): Future[Option[(MediaType, InputStream)]] = {
    Stat.timeFuture(stats.stat("read")) {
      pool {
        try {
          val result = client.getObject(bucket, fullPath(name))

          Some(
            (
              MediaType.parse(result.getObjectMetadata.getContentType).get,
              result.getObjectContent
            )
          )
        } catch {
          case e: AmazonS3Exception if e.getErrorCode == "NoSuchKey" =>
          None
        }
      }
    }
  }

  override def delete(name: String): Future[Unit] = {
    Stat.timeFuture(stats.stat("delete")) {
      pool {
        client.deleteObject(bucket, fullPath(name))
      }
    }
  }

  override def list(directory: String): Future[S3ObjectStorage.ObjectList] = {
    Stat.timeFuture(stats.stat("list")) {
      pool {
        val listRequest = new ListObjectsRequest()
          .withBucketName(bucket)
          .withPrefix(makeStringDirectory(fullPath(directory)))
          .withDelimiter("/")

          convertListResult(client.listObjects(listRequest))
      }
    }
  }

  override def listNext(
    listToken: ObjectStorage.ListToken
  ): Future[S3ObjectStorage.ObjectList] = {
    Stat.timeFuture(stats.stat("listNext")) {
      listToken match {
        case S3ObjectStorage.ListToken(token) =>
          pool {
            convertListResult(client.listNextBatchOfObjects(token))
          }
        case _ =>
          Future.exception(
            new IllegalArgumentException(
              "Programming error. Wrong type of ListToken passed to listNext"
            )
          )
      }
    }
  }

  override def getUrl(name: String): Option[Uri] = {
    Some(Uri(client.getUrl(bucket, fullPath(name)).toURI))
  }

  override def getCreationTime(name: String): Future[Option[Instant]] = {
    Stat.timeFuture(stats.stat("getCreationTime")) {
      pool {
        try {
          val creationTimeInMillis =
            client
            .getObjectMetadata(new GetObjectMetadataRequest(bucket, fullPath(name)))
            .getLastModified.getTime
          Some(Instant.ofEpochMilli(creationTimeInMillis))
        } catch {
          case e: AmazonS3Exception if e.getErrorCode == "NoSuchKey" =>
            None
        }
      }
    }
  }

  private[this] def convertListResult(
    objectListing: ObjectListing
  ): S3ObjectStorage.ObjectList = {
    val listToken = if (objectListing.isTruncated) {
      Some(S3ObjectStorage.ListToken(objectListing))
    } else {
      None
    }

    S3ObjectStorage.ObjectList(
      objectListing.getObjectSummaries.asScala.map(sum => relativePath(sum.getKey))(breakOut),
      objectListing.getCommonPrefixes.asScala.map(
        dir => removeEndingSlash(relativePath(dir))
      )(breakOut),
      listToken
    )
  }

  private[this] def fullPath(name: String): String = {
    makeStringDirectory(path) + name
  }

  /*
   * This function makes sure that the string ends in a `/`. S3 doesn't have the concept of
   * directory but it can be emulated by adding `/` to the key. This allows you to list
   * objects with a given prefix.
   */
  private[this] def makeStringDirectory(prefix: String): String = {
    if (prefix.endsWith("/")) prefix else prefix + "/"
  }

  private[this] def removeEndingSlash(value: String): String = {
    value.stripSuffix("/")
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
    s3Uri: AmazonS3URI
  )(implicit statsReceiver: StatsReceiver): S3ObjectStorage = {
    new S3ObjectStorage(client, s3Uri)
  }

  case class ListToken(token: ObjectListing) extends ObjectStorage.ListToken
  case class ObjectList(
    objects: List[String],
    directories: List[String],
    listToken: Option[ListToken]
  ) extends ObjectStorage.ObjectList
}
