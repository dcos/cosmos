package com.mesosphere.util

import cats.syntax.either._
import com.mesosphere.cosmos.CosmosError
import com.mesosphere.universe
import com.twitter.io.StreamIO
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
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
  ): Either[PackageError, universe.v4.model.Metadata] = {
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

          decode[universe.v4.model.Metadata](metadataString)
            .leftMap(error => InvalidEntry(MetadataPath, error.getClass.getSimpleName))
        }
    } finally {
      zipIn.close()
    }
  }

  def buildPackage(packageContent: OutputStream, metadata: universe.v4.model.Metadata): Unit = {
    val zipOut = new ZipOutputStream(packageContent)
    zipOut.putNextEntry(new ZipEntry(MetadataPath.toString))
    val metadataBytes = metadata.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
    zipOut.write(metadataBytes)
    zipOut.close()
  }

  sealed trait PackageError extends CosmosError


  final case class MissingEntry(path: RelativePath) extends PackageError {
    override def data: Option[JsonObject] = CosmosError.deriveData(this)
    override def message: String = s"Package entry not found: $path"
  }

  object MissingEntry {
    implicit val encoder: Encoder[MissingEntry] = deriveEncoder
  }


  final case class InvalidEntry(
    path: RelativePath,
    failure: String
  ) extends PackageError {
    override def data: Option[JsonObject] = CosmosError.deriveData(this)
    override def message: String = s"Package entry was not JSON or did not match the expected schema: $path"
  }

  object InvalidEntry {
    implicit val encoder: Encoder[InvalidEntry] = deriveEncoder
  }

}
