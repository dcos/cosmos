package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.{label,ServiceMarathonTemplateNotFound, internal, rpc}
import com.mesosphere.cosmos.test.TestUtil

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
  def v3Version(v2model: universe.v2.model.PackageDetails): universe.v2.model.PackageDetails = v2model.copy(
    packagingVersion = universe.v2.model.PackagingVersion("3.0")
  )
  "Conversion[universe.v3.model.PackageDefinition,universe.v2.model.PackageDetails]" - {
    assertResult(v3Version(TestUtil.MaximalV2ModelPackageDetails))(TestUtil.MaximalV3ModelPackageDefinition.as[universe.v2.model.PackageDetails])
    assertResult(v3Version(TestUtil.MinimalV2ModelPackageDetails))(TestUtil.MinimalV3ModelPackageDefinition.as[universe.v2.model.PackageDetails])
  }
  "Conversion[universe.v3.model.V3Package,universe.v2.model.PackageDetails]" - {
    assertResult(v3Version(TestUtil.MinimalV2ModelPackageDetails))(TestUtil.MinimalV3ModelV3PackageDefinition.as[universe.v2.model.PackageDetails])
    assertResult(v3Version(TestUtil.MaximalV2ModelPackageDetails))(TestUtil.MaximalV3ModelV3PackageDefinition.as[universe.v2.model.PackageDetails])
  }
   "Conversion[universe.v3.model.V2Package,universe.v2.model.PackageDetails]" - {
    assertResult(TestUtil.MaximalV2ModelPackageDetails)(TestUtil.MaximalV3ModelV2PackageDefinition.as[universe.v2.model.PackageDetails])
    assertResult(TestUtil.MinimalV2ModelPackageDetails)(TestUtil.MinimalV3ModelV2PackageDefinition.as[universe.v2.model.PackageDetails])
  }
    
}
