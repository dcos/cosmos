package com.mesosphere.universe

import com.mesosphere.universe
import com.mesosphere.universe.bijection.TestUniverseConversions._
import com.mesosphere.util.PackageUtil
import com.twitter.bijection.Conversion.asMethod
import java.io.ByteArrayOutputStream

object TestUtil {

  def buildPackage(supportedPackage: universe.v4.model.SupportedPackageDefinition): Array[Byte] = {
    val (packageData, _) =
      supportedPackage.as[(universe.v4.model.Metadata, universe.v3.model.ReleaseVersion)]

    val bytesOut = new ByteArrayOutputStream()
    PackageUtil.buildPackage(bytesOut, packageData)
    bytesOut.toByteArray
  }

}
