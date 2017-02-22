package com.mesosphere.util

import cats.syntax.either._
import com.mesosphere.universe
import com.mesosphere.universe.v3.circe.Decoders._
import io.circe.Json
import io.circe.jawn.decode
import io.circe.testing.instances._
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks
import scala.util.Failure

final class PackageUtilSpec extends FreeSpec with PropertyChecks {

  import PackageUtilSpec._

  "extractMetadata() fails if metadata.json is not present" in {
    forAll { (zipContents: Map[String, Array[Byte]]) =>
      whenever (!zipContents.contains(MetadataJson)) {
        val zipBytes = encodeZip(zipContents)
        val bytesIn = new ByteArrayInputStream(zipBytes)

        val Failure(error) = PackageUtil.extractMetadata(bytesIn)
        assertResult(PackageUtil.MissingEntry(MetadataPath))(error)
      }
    }
  }

  "extractMetadata() fails if metadata.json could not be decoded" in {
    forAll { (zipContents: Map[String, Array[Byte]], badMetadata: Either[Json, Array[Byte]]) =>
      val metadataBytes = badMetadata.valueOr(_.noSpaces.getBytes(StandardCharsets.UTF_8))
      val metadataString = new String(metadataBytes, StandardCharsets.UTF_8)
      val decodedMetadata = decode[universe.v3.model.Metadata](metadataString)

      whenever (decodedMetadata.isLeft) {
        val contentsWithMetadata = zipContents + (MetadataJson -> metadataBytes)
        val zipBytes = encodeZip(contentsWithMetadata)
        val bytesIn = new ByteArrayInputStream(zipBytes)

        val Failure(error) = PackageUtil.extractMetadata(bytesIn)
        assertResult(PackageUtil.InvalidEntry(MetadataPath))(error)
      }
    }
  }

}

object PackageUtilSpec {

  val MetadataJson: String = "metadata.json"
  val MetadataPath: RelativePath = RelativePath(MetadataJson)

  def encodeZip(contents: Map[String, Array[Byte]]): Array[Byte] = {
    val bytesOut = new ByteArrayOutputStream()
    val zipOut = new ZipOutputStream(bytesOut)

    for ((name, data) <- contents) {
      val entry = new ZipEntry(name)
      zipOut.putNextEntry(entry)
      zipOut.write(data)
    }

    zipOut.close()
    bytesOut.toByteArray
  }

}
