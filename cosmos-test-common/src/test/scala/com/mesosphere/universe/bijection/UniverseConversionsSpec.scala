package com.mesosphere.universe.bijection

import com.mesosphere.universe
import com.mesosphere.universe.bijection.TestUniverseConversions._
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.test.TestingPackages._
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.Injection
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import scala.util.Success

final class UniverseConversionsSpec extends FreeSpec with Matchers with TableDrivenPropertyChecks {

  val packageDefinitionToPackageDetails =
    Table(
      "PackageDefinition" -> "PackageDetails",
      MaximalV3ModelPackageDefinitionV2 -> MaximalV2ModelPackageDetails,
      MinimalV3ModelPackageDefinitionV2 -> MinimalV2ModelPackageDetails,
      MaximalV3ModelPackageDefinitionV3 -> expectV3(MaximalV2ModelPackageDetails),
      MinimalV3ModelPackageDefinitionV3 -> expectV3(MinimalV2ModelPackageDetails),
      MaximalV4ModelPackageDefinitionV4 -> expectV4(
        MaximalV2ModelPackageDetails.copy(name = MaximalV4ModelPackageDefinitionV4.name)
      ),
      MinimalV4ModelPackageDefinitionV4 -> expectV4(
        MinimalV2ModelPackageDetails.copy(name = MinimalV4ModelPackageDefinitionV4.name)
      )
    )

  "Conversion[universe.v4.model.PackageDefinition, universe.v2.model.PackageDetails]" in {
    forAll(packageDefinitionToPackageDetails) { (packageDefinition, packageDetails) =>
      packageDefinition.as[universe.v2.model.PackageDetails] should be(packageDetails)
    }
  }

  val metadataAndReleaseVersion =
    Table(
      "(Metadata, ReleaseVersion)",
      (MaximalV3ModelMetadata, MaxReleaseVersion),
      (MinimalV3ModelMetadata, MinReleaseVersion),
      (MaximalV4ModelMetadata, MaxReleaseVersion),
      (MinimalV4ModelMetadata, MinReleaseVersion)
    )

  "(Metadata, ReleaseVersion) => SupportedPackageDefinition => (Metadata, ReleaseVersion) is the identity fn" in {
    forAll(metadataAndReleaseVersion) { metadataAndReleaseVersion =>
      metadataConversionRoundTrip(metadataAndReleaseVersion) should be(metadataAndReleaseVersion)
    }
  }

  val supportedPackageDefinition =
    Table(
      "SupportedPackageDefinition",
      MaximalV3ModelV3PackageDefinition,
      MinimalV3ModelV3PackageDefinition,
      MaximalV4ModelV4PackageDefinition,
      MinimalV4ModelV4PackageDefinition
    )

  "SupportedPackageDefinition => (Metadata, ReleaseVersion) => SupportedPackageDefinition" - {
    "is almost the identity function" in {
      forAll(supportedPackageDefinition) { original =>
        val roundtrip = metadataConversionAlmostRoundTrip(original) match {
          case v3: universe.v3.model.V3Package =>
            v3.copy(selected = original.selected, command = original.command)
          case v4: universe.v4.model.V4Package =>
            v4.copy(selected = original.selected)
        }
        roundtrip should be(original)
      }
    }
    "should produce empty command and selected parameters" in {
      forAll(supportedPackageDefinition) { original =>
        val roundtrip = metadataConversionAlmostRoundTrip(original)
        roundtrip.command should be(None)
        roundtrip.selected should be(None)
      }
    }
  }

  val v3PackageToPackageDetails =
    Table(
      "V3Package" -> "PackageDetails",
      MaximalV3ModelV3PackageDefinition -> expectV3(MaximalV2ModelPackageDetails),
      MinimalV3ModelV3PackageDefinition -> expectV3(MinimalV2ModelPackageDetails)
    )

  "Conversion[universe.v3.model.V3Package, universe.v2.model.PackageDetails]" in {
    forAll(v3PackageToPackageDetails) { (v3Package, packageDetails) =>
      v3Package.as[universe.v2.model.PackageDetails] should be(packageDetails)
    }
  }

  val v2PackageToPackageDetails =
    Table(
      "V3Package" -> "PackageDetails",
      MaximalV3ModelV2PackageDefinition -> MaximalV2ModelPackageDetails,
      MinimalV3ModelV2PackageDefinition -> MinimalV2ModelPackageDetails
    )

  "Conversion[universe.v3.model.V2Package, universe.v2.model.PackageDetails]" in {
    forAll(v2PackageToPackageDetails) { (v2Package, packageDetails) =>
      v2Package.as[universe.v2.model.PackageDetails] should be(packageDetails)
    }
  }

  "Injection[universe.v3.model.Tag, String]" in {
    val tag = universe.v3.model.Tag("foobar")
    val stringFromTag = tag.as[String]
    assertResult(Success(tag))(Injection.invert[universe.v3.model.Tag, String](stringFromTag))
    assertResult(true)(Injection.invert[universe.v3.model.Tag, String]("foo bar\nfar").isFailure)
    assertResult("foobar")(tag.as[String])
  }

  "Injection[universe.v3.model.License, universe.v2.model.License]" in {
    val lic = universe.v3.model.License(name = "ABC", url = Uri.parse("http://foobar/a/b/c"))
    val licFromV3 = lic.as[universe.v2.model.License]
    val licV2 = universe.v2.model.License(name = "ABC", url = "http://\n:\n/\n")
    assertResult(Success(lic))(Injection.invert[universe.v3.model.License, universe.v2.model.License](licFromV3))
    assertResult(true)(Injection.invert[universe.v3.model.License, universe.v2.model.License](licV2).isFailure)
  }

  "Bijection[universe.v2.model.Command, universe.v3.model.Command]" in {
    val v3 = universe.v3.model.Command(List("foo"))
    val v2FromV3 = v3.as[universe.v2.model.Command]
    assertResult(v3)(v2FromV3.as[universe.v3.model.Command])
  }

  "Injection[universe.v3.model.V2Resource, universe.v3.model.V3Resource]" in {
    val v2 = MaximalV3ModelV2PackageDefinition.resource.get
    val v3FromV2 = v2.as[universe.v3.model.V3Resource]
    assertResult(Success(v2))(Injection.invert[universe.v3.model.V2Resource, universe.v3.model.V3Resource](v3FromV2))
    val v3 = MaximalV3ModelV3PackageDefinition.resource.get
    assertResult(true)(Injection.invert[universe.v3.model.V2Resource, universe.v3.model.V3Resource](v3).isFailure)
  }

  "Bijection[universe.v2.model.Resource, universe.v3.model.V2Resource]" in {
    val v2: universe.v2.model.Resource = MaximalV2Resource
    val v3FromV2 = v2.as[universe.v3.model.V2Resource]
    assertResult(v2)(v3FromV2.as[universe.v2.model.Resource])
  }

  "Bijection[universe.v2.model.PackageDetailsVersion, universe.v3.model.Version]" in {
    val v2: universe.v2.model.PackageDetailsVersion = universe.v2.model.PackageDetailsVersion("2.0")
    val v3FromV2 = v2.as[universe.v3.model.Version]
    assertResult(v2)(v3FromV2.as[universe.v2.model.PackageDetailsVersion])

    val v3: universe.v3.model.Version = universe.v3.model.Version("3.0")
    val v2FromV3 = v3.as[universe.v2.model.PackageDetailsVersion]
    assertResult(v3)(v2FromV3.as[universe.v3.model.Version])
  }

  def metadataConversionRoundTrip(
    metadataAndReleaseVersion: (universe.v4.model.Metadata, universe.v3.model.ReleaseVersion)
  ): (universe.v4.model.Metadata, universe.v3.model.ReleaseVersion) = {
    metadataAndReleaseVersion
      .as[universe.v4.model.SupportedPackageDefinition]
      .as[(universe.v4.model.Metadata, universe.v3.model.ReleaseVersion)]
  }

  def metadataConversionAlmostRoundTrip(
    supportedPackage: universe.v4.model.SupportedPackageDefinition
  ): universe.v4.model.SupportedPackageDefinition = {
    supportedPackage
      .as[(universe.v4.model.Metadata, universe.v3.model.ReleaseVersion)]
      .as[universe.v4.model.SupportedPackageDefinition]
  }

  private[this] def expectV3 = expectVersion("3.0") _

  private[this] def expectV4 = expectVersion("4.0") _

  private[this] def expectVersion(
    version: String
  )(
    v2model: universe.v2.model.PackageDetails
  ): universe.v2.model.PackageDetails = v2model.copy(
    packagingVersion = universe.v2.model.PackagingVersion(version)
  )

}
