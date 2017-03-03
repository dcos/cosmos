package com.mesosphere.util

import cats.syntax.either._
import com.mesosphere.universe
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.circe.Encoders._
import com.twitter.io.StreamIO
import io.circe.JsonObject
import io.circe.jawn.decode
import io.circe.syntax._
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object PackageUtil {

  private val MetadataPath: RelativePath = RelativePath("metadata.json")

  def extractMetadata(
    packageContent: InputStream
  ): Either[PackageError, universe.v3.model.Metadata] = {
    val zipIn = new ZipInputStream(packageContent)

    try {
      Iterator.continually(Option(zipIn.getNextEntry))
        .takeWhile(_.isDefined)
        .flatten
        .find(entry => RelativePath(entry.getName) == MetadataPath)
        .toRight[PackageError](MissingEntry(MetadataPath))
        .flatMap { _ =>
          val metadataBytes = StreamIO.buffer(zipIn).toByteArray
          val metadataString = new String(metadataBytes, StandardCharsets.UTF_8)

          decode[universe.v3.model.Metadata](metadataString)
            .leftMap(error => InvalidEntry(MetadataPath, error))
        }
    } finally {
      zipIn.close()
    }
  }

  def buildPackage(packageContent: OutputStream, metadata: universe.v3.model.Metadata): Unit = {
    val zipOut = new ZipOutputStream(packageContent)
    zipOut.putNextEntry(new ZipEntry(MetadataPath.toString))
    val metadataBytes = metadata.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
    zipOut.write(metadataBytes)
    zipOut.close()
  }

  sealed abstract class PackageError(message: String, cause: Option[Throwable])
    extends RuntimeException(message, cause.orNull) {
    def getData: JsonObject
  }

  case class MissingEntry(path: RelativePath)
    extends PackageError(message = s"Package entry not found: $path", cause = None) {
    override def getData: JsonObject = JsonObject.singleton("path", path.toString.asJson)
  }

  case class InvalidEntry(path: RelativePath, cause: io.circe.Error)
    extends PackageError(
      message = s"Package entry was not JSON or did not match the expected schema: $path",
      cause = Some(cause)
    ) {
    override def getData: JsonObject = JsonObject.fromMap(Map(
      "path" -> path.toString.asJson,
      "failure" -> cause.getClass.getSimpleName.asJson
    ))
  }

}
