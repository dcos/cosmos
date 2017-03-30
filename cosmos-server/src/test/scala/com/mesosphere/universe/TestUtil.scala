package com.mesosphere.universe

import com.mesosphere.universe.bijection.TestUniverseConversions._
import com.mesosphere.util.PackageUtil
import com.twitter.bijection.Conversion.asMethod
import java.io.ByteArrayOutputStream

object TestUtil {

  def buildPackage(supportedPackage: v3.model.SupportedPackageDefinition): Array[Byte] = {
    val (packageData, _) =
      supportedPackage.as[(v3.model.Metadata, v3.model.ReleaseVersion)]

    val bytesOut = new ByteArrayOutputStream()
    PackageUtil.buildPackage(bytesOut, packageData)
    bytesOut.toByteArray
  }

}
