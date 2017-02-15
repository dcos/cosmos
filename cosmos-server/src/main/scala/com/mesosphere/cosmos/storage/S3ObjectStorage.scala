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
  private[this] val basePath = AbsolutePath(s3Uri.getURI.getPath)

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

        val putRequest = new PutObjectRequest(bucket, resolve(path), body, metadata)
          .withCannedAcl(CannedAccessControlList.PublicRead)

          val _ = client.putObject(putRequest)
      }
    }
  }

  override def read(path: AbsolutePath): Future[Option[(MediaType, InputStream)]] = {
    Stat.timeFuture(stats.stat("read")) {
      pool {
        try {
          val result = client.getObject(bucket, resolve(path))

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
        client.deleteObject(bucket, resolve(path))
      }
    }
  }

  override def list(directory: AbsolutePath): Future[S3ObjectStorage.ObjectList] = {
    Stat.timeFuture(stats.stat("list")) {
      pool {
        // S3 doesn't have the concept of directory but it can be emulated by adding `/` to the key.
        // This allows you to list objects with a given prefix.
        val prefix = resolve(directory) + "/"

        val listRequest = new ListObjectsRequest()
          .withBucketName(bucket)
          .withPrefix(prefix)
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
    Some(Uri(client.getUrl(bucket, resolve(path)).toURI))
  }

  override def getCreationTime(path: AbsolutePath): Future[Option[Instant]] = {
    Stat.timeFuture(stats.stat("getCreationTime")) {
      pool {
        try {
          val creationTime =
            client
            .getObjectMetadata(new GetObjectMetadataRequest(bucket, resolve(path)))
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
        sum => relativize(sum.getKey)
      )(breakOut),
      objectListing.getCommonPrefixes.asScala.map(
        dir => relativize(dir)
      )(breakOut),
      listToken
    )
  }

  private[this] def resolve(path: AbsolutePath): String = {
    // TODO cruhland path.relativize(AbsolutePath.Root)
    basePath.resolve(RelativePath(path.toString)).toString
  }

  private[this] def relativize(fullPath: String): AbsolutePath = {
    // TODO cruhland relativize impl?
    AbsolutePath(fullPath.stripPrefix(basePath.toString))
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
