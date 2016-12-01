package com.mesosphere.universe

import com.mesosphere.universe.v3.circe.Encoders._
import io.circe.syntax._
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TestUtil {

  def buildPackage(packageData: v3.model.Metadata): Array[Byte] = {
    // TODO package-add: Factor out common Zip-handling code into utility methods
    val bytesOut = new ByteArrayOutputStream()
    val packageOut = new ZipOutputStream(bytesOut, StandardCharsets.UTF_8)
    packageOut.putNextEntry(new ZipEntry("metadata.json"))
    packageOut.write(packageData.asJson.noSpaces.getBytes(StandardCharsets.UTF_8))
    packageOut.closeEntry()
    packageOut.close()

    bytesOut.toByteArray
  }

}
