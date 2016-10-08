package com.mesosphere.cosmos.render

import cats.data.Xor
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.label.v1.circe.Decoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.common.JsonUtil
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import io.circe.Decoder
import org.scalatest.FreeSpec

import scala.util.{Success, Try}

final class MarathonLabelsSpec extends FreeSpec {

  val RepoUri = Uri.parse("some/repo/uri")

  "requiredLabels" - {

    "MarathonApp.metadataLabel" - {
      "minimal" in {
        val marathonLabels = MarathonLabels(TestingPackages.MinimalV3ModelV2PackageDefinition, RepoUri)
        val packageMetadata =
          decodeRequiredLabel[label.v1.model.PackageMetadata](marathonLabels, MarathonApp.metadataLabel)

        assertCompatible(TestingPackages.MinimalV3ModelV2PackageDefinition, packageMetadata)
      }

      "maximal" in {
        val marathonLabels = MarathonLabels(TestingPackages.MaximalV3ModelV2PackageDefinition, RepoUri)
        val packageMetadata =
          decodeRequiredLabel[label.v1.model.PackageMetadata](marathonLabels, MarathonApp.metadataLabel)

        assertCompatible(TestingPackages.MaximalV3ModelV2PackageDefinition, packageMetadata)
      }
    }

  }
  "nonOverridableLabels" - {
    assertResult(Map())(MarathonLabels(TestingPackages.MinimalV3ModelV2PackageDefinition, RepoUri).nonOverridableLabels)
    assertResult(List(TestingPackages.MaximalV3ModelV2PackageDefinition.command.get))(
      MarathonLabels(TestingPackages.MaximalV3ModelV2PackageDefinition, RepoUri)
        .nonOverridableLabels
        .values
        .map(JsonUtil.decode64[universe.v3.model.Command](_).toOption.get)
    )
  }

  private[this] def decodeRequiredLabel[A: Decoder](labels: MarathonLabels, label: String): A = {
    val base64Json = labels.requiredLabels(MarathonApp.metadataLabel)
    val Xor.Right(data) = JsonUtil.decode64[A](base64Json)
    data
  }

  private[this] def assertCompatible(
    original: universe.v3.model.PackageDefinition,
    result: label.v1.model.PackageMetadata
  ): Unit = {
    // To test that `original` was accurately turned into `result`, reverse the transformation
    val Success(resultPackagingVersion) =
      result.packagingVersion.as[Try[universe.v3.model.PackagingVersion]]

    val resultTags = result.tags.map(tag => universe.v3.model.PackageDefinition.Tag(tag))
    val resultLicenses = result.licenses.map { licenses =>
      licenses.map { case universe.v2.model.License(name, url) =>
        universe.v3.model.License(name, Uri.parse(url))
      }
    }

    assertResult(original.packagingVersion)(resultPackagingVersion)
    assertResult(original.name)(result.name)
    assertResult(original.version)(result.version.as[universe.v3.model.PackageDefinition.Version])
    assertResult(original.maintainer)(result.maintainer)
    assertResult(original.description)(result.description)
    assertResult(original.tags)(resultTags)
    assertResult(original.selected)(result.selected)
    assertResult(original.scm)(result.scm)
    assertResult(original.website)(result.website)
    assertResult(original.framework)(result.framework)
    assertResult(original.preInstallNotes)(result.preInstallNotes)
    assertResult(original.postInstallNotes)(result.postInstallNotes)
    assertResult(original.postUninstallNotes)(result.postUninstallNotes)
    assertResult(original.licenses)(resultLicenses)
    assertResult(original.images)(result.images.as[Option[universe.v3.model.Images]])
  }

}

