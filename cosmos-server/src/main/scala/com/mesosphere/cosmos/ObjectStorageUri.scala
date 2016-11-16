package com.mesosphere.cosmos

import com.amazonaws.services.s3.AmazonS3URI
import com.twitter.util.Try
import com.twitter.util.Throw
import java.nio.file.{Path, Paths}

sealed trait ObjectStorageUri

object ObjectStorageUri {
  def parse(unparsedUri: String): Try[ObjectStorageUri] = {
    val s3UriStart = "s3://"
    val fileUriStart = "file://"
    unparsedUri match {
      case uri if uri.toLowerCase.startsWith(s3UriStart) =>
        Try(S3Uri(new AmazonS3URI(uri)))
      case uri if uri.toLowerCase.startsWith(fileUriStart) =>
        Try(FileUri(Paths.get(uri.drop(fileUriStart.length))))
      case _ =>
        Throw(
          new IllegalArgumentException(
            s"The input [$unparsedUri] is not parsable. Uri's must start with "
              + s"$s3UriStart or $fileUriStart"
          )
        )
    }
  }
}

case class S3Uri(uri: AmazonS3URI) extends ObjectStorageUri

case class FileUri(path: Path) extends ObjectStorageUri
