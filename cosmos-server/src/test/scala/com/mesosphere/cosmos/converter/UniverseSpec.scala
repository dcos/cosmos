package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.{label,ServiceMarathonTemplateNotFound, internal, rpc}
import com.mesosphere.cosmos.converter.Response._
import com.mesosphere.cosmos.test.TestUtil.{MaximalV2ModelPackageDetails,MaximalV3ModelV3PackageDefinition,MaximalV3ModelV2PackageDefinition}

import java.nio.charset.StandardCharsets

import com.mesosphere.universe
import com.mesosphere.universe.v3.model.Cli
import com.mesosphere.universe.common.ByteBuffers

import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.{Try,Return,Throw}

import org.scalatest.FreeSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks.forAll
import org.scalacheck.Gen

import cats.data.Xor
import scala.io.Source

final class UniverseSpec extends FreeSpec {
  "Conversion[universe.v3.model.PackageDefinition,universe.v2.model.PackageDetails]" - {
    "success" in {
      val v2 = MaximalV3ModelV3PackageDefinition.as[universe.v2.model.PackageDetails]
      assertResult(MaximalV2ModelPackageDetails)(v2)
    }
    "failure" in {
//      assert(true)
    }
  }
}
