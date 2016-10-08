package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.bijection.CosmosConversions._
import com.mesosphere.cosmos.converter.Label._
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe.test.TestingPackages
import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod
import org.scalatest.FreeSpec

final class LabelSpec extends FreeSpec {

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

  private[this] def roundTrip[A, B](a: A)(implicit
    aToB: Conversion[A, B],
    bToA: Conversion[B, A]
  ): A = {
    a.as[B].as[A]
  }

}
