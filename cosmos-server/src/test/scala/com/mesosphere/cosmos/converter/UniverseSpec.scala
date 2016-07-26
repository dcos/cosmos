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
  def expectV3(v2model: universe.v2.model.PackageDetails): universe.v2.model.PackageDetails = v2model.copy(
    packagingVersion = universe.v2.model.PackagingVersion("3.0")
  )
  "Conversion[universe.v3.model.PackageDefinition,universe.v2.model.PackageDetails]" in {
    assertResult(expectV3(TestUtil.MaximalV2ModelPackageDetails))(TestUtil.MaximalV3ModelPackageDefinitionV3.as[universe.v2.model.PackageDetails])
    assertResult(expectV3(TestUtil.MinimalV2ModelPackageDetails))(TestUtil.MinimalV3ModelPackageDefinitionV3.as[universe.v2.model.PackageDetails])
  }
  "Conversion[universe.v3.model.V3Package,universe.v2.model.PackageDetails]" in {
    assertResult(expectV3(TestUtil.MinimalV2ModelPackageDetails))(TestUtil.MinimalV3ModelV3PackageDefinition.as[universe.v2.model.PackageDetails])
    assertResult(expectV3(TestUtil.MaximalV2ModelPackageDetails))(TestUtil.MaximalV3ModelV3PackageDefinition.as[universe.v2.model.PackageDetails])
  }
  "Conversion[universe.v3.model.V2Package,universe.v2.model.PackageDetails]" in {
    assertResult(TestUtil.MaximalV2ModelPackageDetails)(TestUtil.MaximalV3ModelV2PackageDefinition.as[universe.v2.model.PackageDetails])
    assertResult(TestUtil.MinimalV2ModelPackageDetails)(TestUtil.MinimalV3ModelV2PackageDefinition.as[universe.v2.model.PackageDetails])
  }
  "Conversion[universe.v3.model.V2Package,internal.model.PackageDefinition]" in {
    def excpectV2Min(p: internal.model.PackageDefinition): internal.model.PackageDefinition = p.copy(
      packagingVersion = universe.v3.model.V2PackagingVersion,
      marathon = Some(TestUtil.MinimalV3ModelV2PackageDefinition.marathon)
    )

    def excpectV2Max(p: internal.model.PackageDefinition): internal.model.PackageDefinition = p.copy(
      packagingVersion = universe.v3.model.V2PackagingVersion,
      marathon = Some(TestUtil.MinimalV3ModelV2PackageDefinition.marathon),
      minDcosReleaseVersion = None,
      resource = Some(universe.v3.model.V3Resource(
        assets = Some(universe.v3.model.Assets(
          uris = Some(Map(
            "foo.tar.gz" -> "http://mesosphere.com/foo.tar.gz",
            "bar.jar"    -> "https://mesosphere.com/bar.jar"
          )),
          container = Some(universe.v3.model.Container(Map(
            "image1" -> "docker/image:1",
            "image2" -> "docker/image:2"
          )))
        )),
        images = Some(universe.v3.model.Images(
          iconSmall = "small.png",
          iconMedium = "medium.png",
          iconLarge = "large.png",
          screenshots = Some(List("ooh.png", "aah.png"))
        ))
      ))
    )
    assertResult(excpectV2Min(TestUtil.MinimalPackageDefinition))(TestUtil.MinimalV3ModelV2PackageDefinition.as[internal.model.PackageDefinition])
    assertResult(excpectV2Min(TestUtil.MinimalPackageDefinition))(TestUtil.MinimalV3ModelPackageDefinitionV2.as[internal.model.PackageDefinition])
    //to internal.model.PackageDefinition
    assertResult(excpectV2Max(TestUtil.MaximalPackageDefinition))(TestUtil.MaximalV3ModelV2PackageDefinition.as[internal.model.PackageDefinition])
    assertResult(excpectV2Max(TestUtil.MaximalPackageDefinition))(TestUtil.MaximalV3ModelPackageDefinitionV2.as[internal.model.PackageDefinition])
  }
  "Conversion[universe.v3.model.V3Package,internal.model.PackageDefinition]" in {
    def excpectV2Ver(p: internal.model.PackageDefinition): internal.model.PackageDefinition = p.copy(
      packagingVersion = universe.v3.model.V3PackagingVersion
    )
    assertResult(excpectV2Ver(TestUtil.MinimalPackageDefinition))(TestUtil.MinimalV3ModelV3PackageDefinition.as[internal.model.PackageDefinition])
    assertResult(excpectV2Ver(TestUtil.MaximalPackageDefinition))(TestUtil.MaximalV3ModelV3PackageDefinition.as[internal.model.PackageDefinition])

    //to internal.model.PackageDefinition
    assertResult(excpectV2Ver(TestUtil.MinimalPackageDefinition))(TestUtil.MinimalV3ModelPackageDefinitionV3.as[internal.model.PackageDefinition])
    assertResult(excpectV2Ver(TestUtil.MaximalPackageDefinition))(TestUtil.MaximalV3ModelPackageDefinitionV3.as[internal.model.PackageDefinition])
  }
  "Conversion[internal.model.PackageDefinition,rpc.v2.model.DescribeResponse]" in {
    assertResult(TestUtil.MinimalV2ModelDescribeResponse)(TestUtil.MinimalPackageDefinition.as[rpc.v2.model.DescribeResponse])
    assertResult(TestUtil.MaximalV2ModelDescribeResponse)(TestUtil.MaximalPackageDefinition.as[rpc.v2.model.DescribeResponse])
  }
  "Bijection[universe.v2.model.PackageDetailsVersion,universe.v3.model.PackageDefinition.Version]" in {
    val v2: universe.v2.model.PackageDetailsVersion  = universe.v2.model.PackageDetailsVersion("2.0")
    val v3FromV2 = v2.as[universe.v3.model.PackageDefinition.Version]
    assertResult(v2)(v3FromV2.as[universe.v2.model.PackageDetailsVersion])

    val v3: universe.v3.model.PackageDefinition.Version = universe.v3.model.PackageDefinition.Version("3.0")
    val v2FromV3 = v3.as[universe.v2.model.PackageDetailsVersion]
    assertResult(v3)(v2FromV3.as[universe.v3.model.PackageDefinition.Version])
  }
  "Conversion[internal.model.PackageDefinition,rpc.v1.model.InstalledPackageInformation]" in {
    assertResult(TestUtil.MaximalInstalledPackageInformation)(TestUtil.MaximalPackageDefinition.as[rpc.v1.model.InstalledPackageInformation])
  }
}
