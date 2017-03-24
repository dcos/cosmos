package com.mesosphere.cosmos.render

import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.label.v1.circe.Decoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import io.circe.Decoder
import io.circe.Json
import io.circe.JsonObject
import io.circe.jawn.decode
import io.circe.jawn.parse
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import scala.util.Success
import scala.util.Try

final class MarathonLabelsSpec extends FreeSpec with Matchers {

  private[this] val repoUri = Uri.parse("some/repo/uri")
  private[this] val options = JsonObject.fromMap(
    Map(
      "integer" -> 7.asJson,
      "string" -> "value".asJson
    )
  )

  "requiredLabels" - {

    "MarathonApp.metadataLabel" - {
      "minimal" in {
        val marathonLabels = MarathonLabels(
          TestingPackages.MinimalV3ModelV2PackageDefinition,
          repoUri,
          options
        )

        val packageMetadata = decodeRequiredLabel[label.v1.model.PackageMetadata](
          marathonLabels,
          MarathonApp.metadataLabel
        )

        assertCompatible(TestingPackages.MinimalV3ModelV2PackageDefinition, packageMetadata)
      }

      "maximal" in {
        val marathonLabels = MarathonLabels(
          TestingPackages.MaximalV3ModelV2PackageDefinition,
          repoUri,
          options
        )
        val packageMetadata = decodeRequiredLabel[label.v1.model.PackageMetadata](
          marathonLabels,
          MarathonApp.metadataLabel
        )

        assertCompatible(TestingPackages.MaximalV3ModelV2PackageDefinition, packageMetadata)
      }
    }

  }

  "nonOverridableLabels" - {
    "minimal" in {
      MarathonLabels(
        TestingPackages.MinimalV3ModelV2PackageDefinition,
        repoUri,
        options
      ).nonOverridableLabels shouldBe
        Map(
          MarathonApp.optionsLabel -> MarathonLabels.encodeForLabel(Json.fromJsonObject(options))
        )
    }

    "maximal" in {
      val labels = MarathonLabels(
        TestingPackages.MaximalV3ModelV2PackageDefinition,
        repoUri,
        options
      ).nonOverridableLabels

      decode64[universe.v3.model.Command](labels(MarathonApp.commandLabel)) shouldBe
        Right(TestingPackages.MaximalV3ModelV2PackageDefinition.command.get)


      parse64(labels(MarathonApp.optionsLabel)) shouldBe Right(Json.fromJsonObject(options))
    }
  }

  private[this] def decodeRequiredLabel[A: Decoder](labels: MarathonLabels, label: String): A = {
    val base64Json = labels.requiredLabels(MarathonApp.metadataLabel)
    val Right(data) = decode64[A](base64Json)
    data
  }

  private[this] def decode64[T: Decoder](value: String): Either[io.circe.Error, T] = {
    decode[T](new String(Base64.getDecoder.decode(value), StandardCharsets.UTF_8))
  }

  private[this] def parse64(value: String): Either[io.circe.Error, Json] = {
    parse(new String(Base64.getDecoder.decode(value), StandardCharsets.UTF_8))
  }

  private[this] def assertCompatible(
    original: universe.v3.model.PackageDefinition,
    result: label.v1.model.PackageMetadata
  ): Assertion = {
    // To test that `original` was accurately turned into `result`, reverse the transformation
    val Success(resultPackagingVersion) =
      result.packagingVersion.as[Try[universe.v3.model.PackagingVersion]]

    val resultTags = result.tags.map(tag => universe.v3.model.PackageDefinition.Tag(tag).get)
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
    assertResult(original.selected.getOrElse(false))(result.selected.get)
    assertResult(original.scm)(result.scm)
    assertResult(original.website)(result.website)
    assertResult(original.framework.getOrElse(false))(result.framework.get)
    assertResult(original.preInstallNotes)(result.preInstallNotes)
    assertResult(original.postInstallNotes)(result.postInstallNotes)
    assertResult(original.postUninstallNotes)(result.postUninstallNotes)
    assertResult(original.licenses)(resultLicenses)
    assertResult(original.images)(result.images.as[Option[universe.v3.model.Images]])
  }

}
