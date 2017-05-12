package com.mesosphere.cosmos

import com.amazonaws.services.s3.AmazonS3URI
import com.netaporter.uri.Uri
import com.twitter.util.Try
import com.twitter.util.Throw
import java.nio.file.Path
import java.nio.file.Paths

sealed trait ObjectStorageUri

object ObjectStorageUri {
  def parse(uri: String): Try[ObjectStorageUri] = {
    Try(Uri.parse(uri)).flatMap(fromUri)
  }

  def fromUri(uri: Uri): Try[ObjectStorageUri] = {
    (uri.scheme, uri.host) match {
      case (Some("s3"), _) =>
        Try(S3Uri(new AmazonS3URI(uri.toURI)))
      case (Some("file"), None) =>
        Try(FileUri(Paths.get(uri.path)))
      case _ =>
        Throw(
          new IllegalArgumentException(
            s"The uri [$uri] is not parsable as an ObjectStorageUri. " +
              "ObjectStorageUri's must start with file:/// or s3://"
          )
        )
    }
  }
}

case class S3Uri(uri: AmazonS3URI) extends ObjectStorageUri {
  override def toString(): String = uri.toString()
}

case class FileUri(path: Path) extends ObjectStorageUri {
  override def toString(): String = s"file://$path"
}
