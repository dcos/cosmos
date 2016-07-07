package com.mesosphere.cosmos

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64

import cats.data.Xor
import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.label.v1.circe.Decoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Decoder, JsonObject}
import org.scalatest.FreeSpec

import scala.util.{Success, Try}

final class MarathonLabelsSpec extends FreeSpec {

  import MarathonLabelsSpec._

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

  private[this] def decodeRequiredLabel[A: Decoder](labels: MarathonLabels, label: String): A = {
    val base64Json = labels.requiredLabels(MarathonApp.metadataLabel)
    val jsonBytes = Base64.getDecoder.decode(base64Json)
    val jsonText = new String(jsonBytes, StandardCharsets.UTF_8)
    val Xor.Right(data) = decode[A](jsonText)
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

object MarathonLabelsSpec {

  val RepoUri = Uri.parse("some/repo/uri")

  val MinimalPackageDefinition = internal.model.PackageDefinition(
    packagingVersion = universe.v3.model.V2PackagingVersion,
    name = "minimal",
    version = universe.v3.model.PackageDefinition.Version("1.2.3"),
    releaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion(0),
    maintainer = "minimal@mesosphere.io",
    description = "A minimal package definition"
  )

  val MaximalPackageDefinition = internal.model.PackageDefinition(
    packagingVersion = universe.v3.model.V3PackagingVersion,
    name = "MAXIMAL",
    version = universe.v3.model.PackageDefinition.Version("9.87.654.3210"),
    releaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion(Int.MaxValue),
    maintainer = "max@mesosphere.io",
    description = "A complete package definition",
    tags = List("all", "the", "things").map(s => universe.v3.model.PackageDefinition.Tag(s)),
    selected = true,
    scm = Some("git"),
    website = Some("mesosphere.com"),
    framework = true,
    preInstallNotes = Some("pre-install message"),
    postInstallNotes = Some("post-install message"),
    postUninstallNotes = Some("post-uninstall message"),
    licenses = Some(List(
      universe.v3.model.License(name = "ABC", url = Uri.parse("a/b/c")),
      universe.v3.model.License(name = "XYZ", url = Uri.parse("x/y/z"))
    )),
    minDcosReleaseVersion = Some(universe.v3.model.DcosReleaseVersionParser.parseUnsafe("1.9.99")),
    marathon = Some(universe.v3.model.Marathon(
      v2AppMustacheTemplate = ByteBuffer.wrap("marathon template".getBytes(StandardCharsets.UTF_8))
    )),
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
      )),
      cli = Some(universe.v3.model.Cli(
        binaries = Some(universe.v3.model.Platforms(
          windows = Some(universe.v3.model.Architectures(
            `x86-64` = universe.v3.model.Binary(
              kind = "windows",
              url = "mesosphere.com/windows.exe",
              contentHash = List(
                universe.v3.model.HashInfo("letters", "abcba"),
                universe.v3.model.HashInfo("numbers", "12321")
              )
            )
          )),
          linux = Some(universe.v3.model.Architectures(
            `x86-64` = universe.v3.model.Binary(
              kind = "linux",
              url = "mesosphere.com/linux",
              contentHash = List(
                universe.v3.model.HashInfo("letters", "ijkji"),
                universe.v3.model.HashInfo("numbers", "13579")
              )
            )
          )),
          darwin = Some(universe.v3.model.Architectures(
            `x86-64` = universe.v3.model.Binary(
              kind = "darwin",
              url = "mesosphere.com/darwin",
              contentHash = List(
                universe.v3.model.HashInfo("letters", "xyzyx"),
                universe.v3.model.HashInfo("numbers", "02468")
              )
            )
          ))
        ))
      ))
    )),
    config = Some(JsonObject.fromMap(Map("foo" -> 42.asJson, "bar" -> "baz".asJson))),
    command = Some(universe.v3.model.Command(
      pip = List("flask", "jinja", "jsonschema")
    ))
  )

}
