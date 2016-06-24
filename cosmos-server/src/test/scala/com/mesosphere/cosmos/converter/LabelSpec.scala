package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.MarathonLabelsSpec
import com.mesosphere.cosmos.converter.Label._
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.rpc
import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod
import org.scalatest.FreeSpec

final class LabelSpec extends FreeSpec {

  "label.v1.model.PackageMetadata <=> rpc.v1.model.InstalledPackageInformation" - {

    "minimal" in {
      val packageDefinition = MarathonLabelsSpec.MinimalPackageDefinition
      val packageMetadata = packageDefinition.as[label.v1.model.PackageMetadata]

      val result = roundTrip[label.v1.model.PackageMetadata, rpc.v1.model.InstalledPackageInformation](
        packageMetadata
      )

      assertResult(packageMetadata)(result)
    }

    "maximal" in {
      val packageDefinition = MarathonLabelsSpec.MaximalPackageDefinition
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
