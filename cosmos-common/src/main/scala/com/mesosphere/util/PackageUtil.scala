package com.mesosphere.util

import cats.syntax.either._
import com.mesosphere.universe
import com.mesosphere.universe.v3.circe.Decoders._
import com.twitter.io.StreamIO
import io.circe.jawn.decode
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import scala.util.Try

object PackageUtil {

  val MetadataPath: RelativePath = RelativePath("metadata.json")

  def extractMetadata(packageContent: InputStream): Try[universe.v3.model.Metadata] = {
    val zipIn = new ZipInputStream(packageContent)

    val result = Try {
      val foundMetadata = Iterator.continually(Option(zipIn.getNextEntry))
        .takeWhile(_.isDefined)
        .flatten
        .exists(entry => RelativePath(entry.getName) == MetadataPath)

      if (foundMetadata) {
        val metadataBytes = StreamIO.buffer(zipIn).toByteArray
        val metadataString = new String(metadataBytes, StandardCharsets.UTF_8)

        decode[universe.v3.model.Metadata](metadataString)
          .valueOr(error => throw InvalidEntry(MetadataPath, error))
      } else {
        throw MissingEntry(MetadataPath)
      }
    }

    // If close() throws, let the exception propagate, since it's likely the result of a bug
    zipIn.close()
    result
  }

  def buildPackage(packageContent: OutputStream, metadata: universe.v3.model.Metadata): Unit = ???

  sealed abstract class PackageError(override val getMessage: String) extends RuntimeException

  case class MissingEntry(path: RelativePath)
    extends PackageError(s"Package entry not found: $path")

  case class InvalidEntry(path: RelativePath, cause: io.circe.Error)
    extends PackageError(s"Package entry was not JSON or did not match the expected schema: $path")

}
