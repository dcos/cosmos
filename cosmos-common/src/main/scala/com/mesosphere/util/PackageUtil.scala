package com.mesosphere.util

import com.mesosphere.universe
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PackageUtil {

  def extractMetadata(packageContent: InputStream): universe.v3.model.Metadata = ???

  def buildPackage(packageContent: OutputStream, metadata: universe.v3.model.Metadata): Unit = {
    val zipOut = new ZipOutputStream(packageContent)
    zipOut.putNextEntry(new ZipEntry(""))
  }

}
