package com.mesosphere.universe

import com.mesosphere.universe.bijection.TestUniverseConversions._
import com.mesosphere.util.PackageUtil
import com.twitter.bijection.Conversion.asMethod
import java.io.ByteArrayOutputStream

object TestUtil {

  def buildPackage(v3Package: v3.model.V3Package): Array[Byte] = {
    val (packageData, _) =
      v3Package.as[(v3.model.Metadata, v3.model.PackageDefinition.ReleaseVersion)]

    val bytesOut = new ByteArrayOutputStream()
    PackageUtil.buildPackage(bytesOut, packageData)
    bytesOut.toByteArray
  }

}
