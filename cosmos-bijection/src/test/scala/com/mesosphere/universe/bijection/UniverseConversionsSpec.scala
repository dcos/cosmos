package com.mesosphere.universe.bijection

import com.mesosphere.universe
import com.mesosphere.universe.bijection.TestUniverseConversions._
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.test.TestingPackages._
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.Injection
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import scala.util.Success

final class UniverseConversionsSpec extends FreeSpec with Matchers {

  "Conversion[universe.v3.model.PackageDefinition, universe.v2.model.PackageDetails]" - {
    "Max v3v3 -> Max v2" in {
      assertResult(expectV3(MaximalV2ModelPackageDetails))(MaximalV3ModelPackageDefinitionV3.as[universe.v2.model.PackageDetails])
    }
    "Min v3v3 -> Min v2" in {
      assertResult(expectV3(MinimalV2ModelPackageDetails))(MinimalV3ModelPackageDefinitionV3.as[universe.v2.model.PackageDetails])
    }
    "Max v3v2 -> Max v3" in {
      assertResult(MaximalV2ModelPackageDetails)(MaximalV3ModelPackageDefinitionV2.as[universe.v2.model.PackageDetails])
    }
    "Min v3v2 -> Min v3" in {
      assertResult(MinimalV2ModelPackageDetails)(MinimalV3ModelPackageDefinitionV2.as[universe.v2.model.PackageDetails])
    }
  }

  "(Metadata, ReleaseVersion) => V3Package => (Metadata, ReleaseVersion) is the identity fn" - {

    "Max metadata" in {
      val releaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion(Long.MaxValue).get
      testCase((MaximalV3ModelMetadata, releaseVersion))
    }

    "Min metadata" in {
      val releaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion(0L).get
      testCase((MinimalV3ModelMetadata, releaseVersion))
    }

    def testCase(
      value: (universe.v3.model.Metadata, universe.v3.model.PackageDefinition.ReleaseVersion)
    ): Assertion = {
      assertResult(value) {
        value
          .as[universe.v3.model.V3Package]
          .as[(universe.v3.model.Metadata, universe.v3.model.PackageDefinition.ReleaseVersion)]
      }
    }

  }

  "V3Package => (Metadata, ReleaseVersion) => V3Package is almost the identity function" - {

    "Max V3Package" in {
      testCase(MaximalV3ModelV3PackageDefinition)
    }

    "Min V3Package" in {
      testCase(MinimalV3ModelV3PackageDefinition)
    }

    def testCase(v3Package: universe.v3.model.V3Package): Assertion = {
      val selected = v3Package.selected
      val command = v3Package.command

      val roundTrip = v3Package
        .as[(universe.v3.model.Metadata, universe.v3.model.PackageDefinition.ReleaseVersion)]
        .as[universe.v3.model.V3Package]

      assert(roundTrip.selected.isEmpty)
      assert(roundTrip.command.isEmpty)
      assertResult(v3Package)(roundTrip.copy(selected = selected, command = command))
    }

  }

  "Conversion[universe.v3.model.V3Package, universe.v2.model.PackageDetails]" - {
    "Min v3v3 -> Min v2" in {
      assertResult(expectV3(MinimalV2ModelPackageDetails))(MinimalV3ModelV3PackageDefinition.as[universe.v2.model.PackageDetails])
    }
    "Max v3v3 -> Max v2" in {
      assertResult(expectV3(MaximalV2ModelPackageDetails))(MaximalV3ModelV3PackageDefinition.as[universe.v2.model.PackageDetails])
    }
  }

  "Conversion[universe.v3.model.V2Package, universe.v2.model.PackageDetails]" in {
    assertResult(MaximalV2ModelPackageDetails)(MaximalV3ModelV2PackageDefinition.as[universe.v2.model.PackageDetails])
    assertResult(MinimalV2ModelPackageDetails)(MinimalV3ModelV2PackageDefinition.as[universe.v2.model.PackageDetails])
  }

  "Injection[universe.v3.model.PackageDefinition.Tag, String]" in {
    val tag = universe.v3.model.Tag("foobar").get
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

  private[this] def expectV3(v2model: universe.v2.model.PackageDetails): universe.v2.model.PackageDetails = v2model.copy(
    packagingVersion = universe.v2.model.PackagingVersion("3.0")
  )

}
