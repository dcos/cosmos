package com.mesosphere.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
        val bytesOut = new ByteArrayOutputStream()
        val zipOut = new ZipOutputStream(bytesOut)

        for ((name, data) <- zipContents) {
          val entry = new ZipEntry(name)
          zipOut.putNextEntry(entry)
          zipOut.write(data)
        }

        zipOut.close()

        val bytesIn = new ByteArrayInputStream(bytesOut.toByteArray)
        val Failure(error) = PackageUtil.extractMetadata(bytesIn)
        assertResult(PackageUtil.EntryNotFound(AbsolutePath.Root / MetadataJson))(error)
      }
    }
  }

}

object PackageUtilSpec {
  val MetadataJson: String = "metadata.json"
}
