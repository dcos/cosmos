package com.mesosphere.cosmos.converter

import com.mesosphere.Generators.Implicits._
import com.mesosphere.cosmos.converter.Response._
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

final class LabelSpec extends FreeSpec with PropertyChecks with Matchers {

  "universe.v4.model.PackageDefinition => rpc.v1.model.InstalledPackageInformation" in {
    forAll { pkgDef: universe.v4.model.PackageDefinition =>
      val actual = pkgDef.as[rpc.v1.model.InstalledPackageInformation]

      /* Checking the content of `actual` is mechanically the same as the conversion code. Instead
       * let's check that it succeeded.
       */
      Option(actual) should not be None
      actual shouldBe a[rpc.v1.model.InstalledPackageInformation]
    }
  }

}
