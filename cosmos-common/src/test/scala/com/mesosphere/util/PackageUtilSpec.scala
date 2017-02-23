package com.mesosphere.util

import cats.syntax.either._
import com.mesosphere.Generators.Implicits._
import com.mesosphere.universe
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.circe.Encoders._
import io.circe.Json
import io.circe.jawn.decode
import io.circe.syntax._
import io.circe.testing.instances._
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.scalacheck.Shapeless._
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks
import scala.util.Failure
import scala.util.Success

final class PackageUtilSpec extends FreeSpec with PropertyChecks {

  import PackageUtilSpec._

  "extractMetadata() fails if metadata.json is not present" in {
    forAll { (zipContents: Map[String, Array[Byte]]) =>
      whenever (!zipContents.contains(MetadataJson)) {
        val bytesIn = encodeZip(zipContents)

        val Failure(error) = PackageUtil.extractMetadata(bytesIn)
        assertResult(PackageUtil.MissingEntry(MetadataPath))(error)
        assert(bytesIn.isClosed)
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
        val bytesIn = encodeZip(contentsWithMetadata)

        val Failure(PackageUtil.InvalidEntry(path, _)) = PackageUtil.extractMetadata(bytesIn)
        assertResult(MetadataPath)(path)
        assert(bytesIn.isClosed)
      }
    }
  }

  "extractMetadata() succeeds if metadata.json could be decoded" in {
    forAll { (zipContents: Map[String, Array[Byte]], goodMetadata: universe.v3.model.Metadata) =>
      val metadataBytes = goodMetadata.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
      val contentsWithMetadata = zipContents + (MetadataJson -> metadataBytes)
      val bytesIn = encodeZip(contentsWithMetadata)

      val Success(extractedMetadata) = PackageUtil.extractMetadata(bytesIn)
      assertResult(goodMetadata)(extractedMetadata)
      assert(bytesIn.isClosed)
    }
  }

  "extractMetadata() captures unexpected errors with Try" in {
    val badInputStream = new InputStream with CloseDetector {
      override def read() = throw new UnsupportedOperationException
    }

    val Failure(error) = PackageUtil.extractMetadata(badInputStream)
    assert(error.isInstanceOf[UnsupportedOperationException])
    assert(badInputStream.isClosed)
  }

  "buildPackage() creates a package such that extractMetadata() recovers the input" in {
    forAll { (inputMetadata: universe.v3.model.Metadata) =>
      val bytesOut = new ByteArrayOutputStream() with CloseDetector
      PackageUtil.buildPackage(bytesOut, inputMetadata)
      assert(bytesOut.isClosed)

      val bytesIn = new ByteArrayInputStream(bytesOut.toByteArray)
      val Success(outputMetadata) = PackageUtil.extractMetadata(bytesIn)
      assertResult(inputMetadata)(outputMetadata)
    }
  }

}

object PackageUtilSpec {

  val MetadataJson: String = "metadata.json"
  val MetadataPath: RelativePath = RelativePath(MetadataJson)

  def encodeZip(contents: Map[String, Array[Byte]]): InputStream with CloseDetector = {
    val bytesOut = new ByteArrayOutputStream()
    val zipOut = new ZipOutputStream(bytesOut)

    for ((name, data) <- contents) {
      val entry = new ZipEntry(name)
      zipOut.putNextEntry(entry)
      zipOut.write(data)
    }

    zipOut.close()
    new ByteArrayInputStream(bytesOut.toByteArray) with CloseDetector
  }

  trait CloseDetector extends AutoCloseable {

    var isClosed: Boolean = false

    abstract override def close(): Unit = try { super.close() } finally { isClosed = true }

  }

}
