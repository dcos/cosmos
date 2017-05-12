package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.bijection.CosmosConversions._
import com.mesosphere.cosmos.converter.Label._
import com.mesosphere.cosmos.converter.Response._
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.rpc
import com.mesosphere.Generators.Implicits._
import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

final class LabelSpec extends FreeSpec with PropertyChecks with Matchers {

  "label.v1.model.PackageMetadata <=> rpc.v1.model.InstalledPackageInformation" - {

    "minimal" in {
      val packageDefinition = TestingPackages.MinimalV3ModelV2PackageDefinition
      val packageMetadata = packageDefinition.as[label.v1.model.PackageMetadata]

      val result = roundTrip[label.v1.model.PackageMetadata, rpc.v1.model.InstalledPackageInformation](
        packageMetadata
      )

      assertResult(packageMetadata)(result)
    }

    "maximal" in {
      val packageDefinition = TestingPackages.MaximalV3ModelV2PackageDefinition
      val packageMetadata = packageDefinition.as[label.v1.model.PackageMetadata]

      val result = roundTrip[label.v1.model.PackageMetadata, rpc.v1.model.InstalledPackageInformation](
        packageMetadata
      )

      assertResult(packageMetadata)(result)
    }

  }

  "universe.v4.model.PackageDefinition => rpc.v1.model.InstalledPackageInformation" in {
    forAll { (pkgDef: universe.v4.model.PackageDefinition) =>
      val actual = pkgDef.as[rpc.v1.model.InstalledPackageInformation]

      /* Checking the content of `actual` is mechanically the same as the conversion code. Instead
       * let's check that it succeeded.
       */
      Option(actual) should not be None
      actual shouldBe a[rpc.v1.model.InstalledPackageInformation]
    }
  }

  private[this] def roundTrip[A, B](a: A)(implicit
    aToB: Conversion[A, B],
    bToA: Conversion[B, A]
  ): A = {
    a.as[B].as[A]
  }

}
