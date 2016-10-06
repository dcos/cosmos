package com.mesosphere.cosmos

import com.mesosphere.cosmos.label.v1.circe.Decoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.cosmos.test.TestUtil.{MinimalPackageDefinition,MaximalPackageDefinition,RepoUri}
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.model.Command
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import io.circe.Decoder
import org.scalatest.FreeSpec
import com.mesosphere.universe.common.JsonUtil
import cats.data.Xor

import scala.util.{Success, Try}

final class MarathonLabelsSpec extends FreeSpec {


  "requiredLabels" - {

    "MarathonApp.metadataLabel" - {
      "minimal" in {
        val marathonLabels = MarathonLabels(MinimalPackageDefinition, RepoUri)
        val packageMetadata =
          decodeRequiredLabel[label.v1.model.PackageMetadata](marathonLabels, MarathonApp.metadataLabel)

        assertCompatible(MinimalPackageDefinition, packageMetadata)
      }

      "maximal" in {
        val marathonLabels = MarathonLabels(MaximalPackageDefinition, RepoUri)
        val packageMetadata =
          decodeRequiredLabel[label.v1.model.PackageMetadata](marathonLabels, MarathonApp.metadataLabel)

        assertCompatible(MaximalPackageDefinition, packageMetadata)
      }
    }

  }
  "nonOverridableLabels" - {
    assertResult(Map())(MarathonLabels(MinimalPackageDefinition, RepoUri).nonOverridableLabels)
    assertResult(List(MaximalPackageDefinition.command.get))(MarathonLabels(MaximalPackageDefinition, RepoUri).nonOverridableLabels.values.map(JsonUtil.decode64[Command](_).toOption.get))
  }

  private[this] def decodeRequiredLabel[A: Decoder](labels: MarathonLabels, label: String): A = {
    val base64Json = labels.requiredLabels(MarathonApp.metadataLabel)
    val Xor.Right(data) = JsonUtil.decode64[A](base64Json)
    data
  }

  private[this] def assertCompatible(
    original: internal.model.PackageDefinition,
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
    assertResult(Some(original.selected))(result.selected)
    assertResult(original.scm)(result.scm)
    assertResult(original.website)(result.website)
    assertResult(Some(original.framework))(result.framework)
    assertResult(original.preInstallNotes)(result.preInstallNotes)
    assertResult(original.postInstallNotes)(result.postInstallNotes)
    assertResult(original.postUninstallNotes)(result.postUninstallNotes)
    assertResult(original.licenses)(resultLicenses)
    assertResult(original.resource.flatMap(_.images))(result.images.as[Option[universe.v3.model.Images]])
  }

}

