package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.InternalPackageDefinition._
import com.mesosphere.cosmos.test.TestUtil
import com.mesosphere.cosmos.{internal, rpc}
import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import com.twitter.bijection.Conversion.asMethod
import org.scalatest.FreeSpec

final class InternalPackageDefinitionSpec extends FreeSpec {

  "Conversion[universe.v3.model.V2Package, internal.model.PackageDefinition]" in {
    def expectV2Min(p: internal.model.PackageDefinition): internal.model.PackageDefinition = p.copy(
      packagingVersion = universe.v3.model.V2PackagingVersion,
      marathon = Some(TestingPackages.MinimalV3ModelV2PackageDefinition.marathon)
    )

    def expectV2Max(p: internal.model.PackageDefinition): internal.model.PackageDefinition = p.copy(
      packagingVersion = universe.v3.model.V2PackagingVersion,
      marathon = Some(TestingPackages.MinimalV3ModelV2PackageDefinition.marathon),
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
          iconSmall = Some("small.png"),
          iconMedium = Some("medium.png"),
          iconLarge = Some("large.png"),
          screenshots = Some(List("ooh.png", "aah.png"))
        ))
      ))
    )
    assertResult(expectV2Min(TestUtil.MinimalPackageDefinition))(TestingPackages.MinimalV3ModelV2PackageDefinition.as[internal.model.PackageDefinition])
    assertResult(expectV2Min(TestUtil.MinimalPackageDefinition))(TestingPackages.MinimalV3ModelPackageDefinitionV2.as[internal.model.PackageDefinition])
    //to internal.model.PackageDefinition
    assertResult(expectV2Max(TestUtil.MaximalPackageDefinition))(TestingPackages.MaximalV3ModelV2PackageDefinition.as[internal.model.PackageDefinition])
    assertResult(expectV2Max(TestUtil.MaximalPackageDefinition))(TestingPackages.MaximalV3ModelPackageDefinitionV2.as[internal.model.PackageDefinition])
  }

  "Conversion[universe.v3.model.V3Package, internal.model.PackageDefinition]" in {
    def expectV2Ver(p: internal.model.PackageDefinition): internal.model.PackageDefinition = p.copy(
      packagingVersion = universe.v3.model.V3PackagingVersion
    )
    assertResult(expectV2Ver(TestUtil.MinimalPackageDefinition))(TestingPackages.MinimalV3ModelV3PackageDefinition.as[internal.model.PackageDefinition])
    assertResult(expectV2Ver(TestUtil.MaximalPackageDefinition))(TestingPackages.MaximalV3ModelV3PackageDefinition.as[internal.model.PackageDefinition])

    //to internal.model.PackageDefinition
    assertResult(expectV2Ver(TestUtil.MinimalPackageDefinition))(TestingPackages.MinimalV3ModelPackageDefinitionV3.as[internal.model.PackageDefinition])
    assertResult(expectV2Ver(TestUtil.MaximalPackageDefinition))(TestingPackages.MaximalV3ModelPackageDefinitionV3.as[internal.model.PackageDefinition])
  }

  "Conversion[internal.model.PackageDefinition, rpc.v2.model.DescribeResponse]" in {
    assertResult(TestUtil.MinimalV2ModelDescribeResponse)(TestUtil.MinimalPackageDefinition.as[rpc.v2.model.DescribeResponse])
    assertResult(TestUtil.MaximalV2ModelDescribeResponse)(TestUtil.MaximalPackageDefinition.as[rpc.v2.model.DescribeResponse])
  }

  "Conversion[internal.model.PackageDefinition, rpc.v1.model.InstalledPackageInformation]" in {
    assertResult(TestUtil.MaximalInstalledPackageInformation)(TestUtil.MaximalPackageDefinition.as[rpc.v1.model.InstalledPackageInformation])
  }
}
