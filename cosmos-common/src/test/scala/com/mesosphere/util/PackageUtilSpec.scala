package com.mesosphere.util

import com.mesosphere.Generators.Implicits._
import com.mesosphere.universe
import io.circe.testing.instances._
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks
import org.scalacheck.Shapeless._

final class PackageUtilSpec extends FreeSpec with PropertyChecks {

  "PackageUtil.buildPackage" in {
    forAll { (metadata: universe.v3.model.Metadata) =>
      val packageOut = new ByteArrayOutputStream
      PackageUtil.buildPackage(packageOut, metadata)

      val packageIn = new ByteArrayInputStream(packageOut.toByteArray)
      val zipIn = new ZipInputStream(packageIn)
      val firstEntry = Option(zipIn.getNextEntry)
      assert(firstEntry.isDefined)
    }
  }

}
