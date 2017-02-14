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
import com.mesosphere.util.AbsolutePath
import com.mesosphere.util.RelativePath
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
  private[this] val basePath = AbsolutePath(s3Uri.getURI.getPath).right.get

  private[this] val stats = statsReceiver.scope(s"S3ObjectStorage($bucket, $basePath)")

  override def write(
    path: AbsolutePath,
    body: InputStream,
    contentLength: Long,
    contentType: MediaType
  ): Future[Unit] = {
    Stat.timeFuture(stats.stat("write")) {
      pool {
        val metadata = new ObjectMetadata()
        metadata.setContentLength(contentLength)
        metadata.setContentType(contentType.show)

        val putRequest = new PutObjectRequest(bucket, fullPath(path), body, metadata)
          .withCannedAcl(CannedAccessControlList.PublicRead)

          val _ = client.putObject(putRequest)
      }
    }
  }

  override def read(path: AbsolutePath): Future[Option[(MediaType, InputStream)]] = {
    Stat.timeFuture(stats.stat("read")) {
      pool {
        try {
          val result = client.getObject(bucket, fullPath(path))

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

  override def delete(path: AbsolutePath): Future[Unit] = {
    Stat.timeFuture(stats.stat("delete")) {
      pool {
        client.deleteObject(bucket, fullPath(path))
      }
    }
  }

  override def list(directory: AbsolutePath): Future[S3ObjectStorage.ObjectList] = {
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

  override def getUrl(path: AbsolutePath): Option[Uri] = {
    Some(Uri(client.getUrl(bucket, fullPath(path)).toURI))
  }

  override def getCreationTime(path: AbsolutePath): Future[Option[Instant]] = {
    Stat.timeFuture(stats.stat("getCreationTime")) {
      pool {
        try {
          val creationTime =
            client
            .getObjectMetadata(new GetObjectMetadataRequest(bucket, fullPath(path)))
            .getLastModified.toInstant
          Some(creationTime)
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
      objectListing.getObjectSummaries.asScala.map(
        sum => relativePath(AbsolutePath(sum.getKey).right.get)
      )(breakOut),
      objectListing.getCommonPrefixes.asScala.map(
        dir => relativePath(AbsolutePath(dir).right.get)
      )(breakOut),
      listToken
    )
  }

  private[this] def fullPath(path: AbsolutePath): String = {
    (basePath / RelativePath(path.toString).right.get).toString
  }

  /*
   * This function makes sure that the string ends in a `/`. S3 doesn't have the concept of
   * directory but it can be emulated by adding `/` to the key. This allows you to list
   * objects with a given prefix.
   */
  private[this] def makeStringDirectory(prefix: String): String = {
    if (prefix.endsWith("/")) prefix else prefix + "/"
  }

  private[this] def relativePath(fullPath: AbsolutePath): AbsolutePath = {
    AbsolutePath(fullPath.toString.stripPrefix(basePath.toString)).right.get
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
    objects: List[AbsolutePath],
    directories: List[AbsolutePath],
    listToken: Option[ListToken]
  ) extends ObjectStorage.ObjectList
}
