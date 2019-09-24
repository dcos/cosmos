package com.mesosphere.universe.test

import com.mesosphere.universe
import io.lemonlabs.uri.Uri
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.scalatest.prop.TableFor1
import org.scalatest.prop.TableFor2
import scala.util.Random

object TestingPackages {
  val PackagingVersion = universe.v3.model.V3PackagingVersion
  val Name = "MAXIMAL"
  val MinimalName = "minimal"
  val Version = universe.v3.model.Version("9.87.654.3210")
  val Maintainer = "max@mesosphere.io"
  val MaxReleaseVersion = universe.v3.model.ReleaseVersion(Long.MaxValue)
  val MinReleaseVersion = universe.v3.model.ReleaseVersion(0L)
  val Description = "A complete package definition"
  val MarathonTemplate = Some(universe.v3.model.Marathon(
    v2AppMustacheTemplate = ByteBuffer.wrap("marathon template".getBytes(StandardCharsets.UTF_8))
  ))
  val Tags = List("all", "the", "things").map(
    s => universe.v3.model.Tag(s)
  )
  val Scm = Some("git")
  val Website = Some("mesosphere.com")
  val Framework = Some(true)
  val PreInstallNotes = Some("pre-install message")
  val PostInstallNotes = Some("post-install message")
  val PostUninstallNotes = Some("post-uninstall message")
  val Licenses = Some(List(
    universe.v3.model.License(name = "ABC", url = Uri.parse("http://foobar/a/b/c")),
    universe.v3.model.License(name = "XYZ", url = Uri.parse("http://foobar/x/y/z"))
  ))
  val MinDcosReleaseVersion = Some(universe.v3.model.DcosReleaseVersionParser.parseUnsafe("1.9.99"))
  val lastUpdated = Some(System.currentTimeMillis())
  val hasKnownIssues = Some(Random.nextBoolean())

  private val iconSmall = Some("small.png")
  private val iconMedium = Some("medium.png")
  private val iconLarge = Some("large.png")
  private val screenshots = Some(List("ooh.png", "aah.png"))

  val Resource = Some(universe.v3.model.V3Resource(
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
      iconSmall,
      iconMedium,
      iconLarge,
      screenshots
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
  ))
  val Config = Some(JsonObject.fromMap(Map("foo" -> 42.asJson, "bar" -> "baz".asJson)))

  val MaximalV3ModelV3PackageDefinition: universe.v3.model.V3Package = universe.v3.model.V3Package(
    PackagingVersion,
    Name,
    Version,
    MaxReleaseVersion,
    Maintainer,
    Description,
    Tags,
    None,
    Scm,
    Website,
    Framework,
    PreInstallNotes,
    PostInstallNotes,
    PostUninstallNotes,
    Licenses,
    MinDcosReleaseVersion,
    MarathonTemplate,
    Resource,
    Config,
    command = Some(universe.v3.model.Command(
      pip = List("flask", "jinja", "jsonschema")
    )),
    lastUpdated=lastUpdated,
    hasKnownIssues=hasKnownIssues
  )

  val MinimalV3ModelV3PackageDefinition: universe.v3.model.V3Package = universe.v3.model.V3Package(
    packagingVersion = universe.v3.model.V3PackagingVersion,
    name = "minimal",
    version = universe.v3.model.Version("1.2.3"),
    releaseVersion = MinReleaseVersion,
    maintainer = "minimal@mesosphere.io",
    description = "A minimal package definition"
  )


  val MaximalV3ModelV2PackageDefinition: universe.v3.model.V2Package = universe.v3.model.V2Package(
    packagingVersion = universe.v3.model.V2PackagingVersion,
    name = MaximalV3ModelV3PackageDefinition.name,
    version = MaximalV3ModelV3PackageDefinition.version,
    releaseVersion = MaximalV3ModelV3PackageDefinition.releaseVersion,
    maintainer = MaximalV3ModelV3PackageDefinition.maintainer,
    description = MaximalV3ModelV3PackageDefinition.description,
    marathon = MaximalV3ModelV3PackageDefinition.marathon.get,
    tags = MaximalV3ModelV3PackageDefinition.tags,
    selected = MaximalV3ModelV3PackageDefinition.selected,
    scm = MaximalV3ModelV3PackageDefinition.scm,
    website = MaximalV3ModelV3PackageDefinition.website,
    framework = MaximalV3ModelV3PackageDefinition.framework,
    preInstallNotes = MaximalV3ModelV3PackageDefinition.preInstallNotes,
    postInstallNotes = MaximalV3ModelV3PackageDefinition.postInstallNotes,
    postUninstallNotes = MaximalV3ModelV3PackageDefinition.postUninstallNotes,
    licenses = MaximalV3ModelV3PackageDefinition.licenses,
    resource = Some(universe.v3.model.V2Resource(
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
        iconSmall,
        iconMedium,
        iconLarge,
        screenshots
      ))
    )),
    config = MaximalV3ModelV3PackageDefinition.config,
    command = MaximalV3ModelV3PackageDefinition.command
  )


  val MinimalV3ModelV2PackageDefinition: universe.v3.model.V2Package = universe.v3.model.V2Package(
    packagingVersion = universe.v3.model.V2PackagingVersion,
    name = MinimalV3ModelV3PackageDefinition.name,
    version = MinimalV3ModelV3PackageDefinition.version,
    releaseVersion = MinimalV3ModelV3PackageDefinition.releaseVersion,
    maintainer = MinimalV3ModelV3PackageDefinition.maintainer,
    description = MinimalV3ModelV3PackageDefinition.description,
    marathon = MaximalV3ModelV3PackageDefinition.marathon.get
  )


  val MaximalV2ModelPackageDetails = universe.v2.model.PackageDetails(
    packagingVersion = universe.v2.model.PackagingVersion("2.0"),
    name = MaximalV3ModelV2PackageDefinition.name,
    version = universe.v2.model.PackageDetailsVersion(MaximalV3ModelV2PackageDefinition.version.toString),
    maintainer = MaximalV3ModelV2PackageDefinition.maintainer,
    description = MaximalV3ModelV2PackageDefinition.description,
    tags = List("all", "the", "things"),
    selected = MaximalV3ModelV2PackageDefinition.selected,
    scm = MaximalV3ModelV2PackageDefinition.scm,
    website = MaximalV3ModelV2PackageDefinition.website,
    framework = MaximalV3ModelV2PackageDefinition.framework,
    preInstallNotes = MaximalV3ModelV2PackageDefinition.preInstallNotes,
    postInstallNotes = MaximalV3ModelV2PackageDefinition.postInstallNotes,
    postUninstallNotes = MaximalV3ModelV2PackageDefinition.postUninstallNotes,
    licenses = Some(List(
      universe.v2.model.License(name = "ABC", url = "http://foobar/a/b/c"),
      universe.v2.model.License(name = "XYZ", url = "http://foobar/x/y/z")
    ))
  )

  val MinimalV2ModelPackageDetails = universe.v2.model.PackageDetails(
    packagingVersion = universe.v2.model.PackagingVersion("2.0"),
    name = MinimalV3ModelV3PackageDefinition.name,
    version = universe.v2.model.PackageDetailsVersion(MinimalV3ModelV3PackageDefinition.version.toString),
    maintainer = MinimalV3ModelV3PackageDefinition.maintainer,
    description = MinimalV3ModelV3PackageDefinition.description
  )

  val MaximalV3ModelPackageDefinitionV2: universe.v4.model.PackageDefinition = MaximalV3ModelV2PackageDefinition
  val MinimalV3ModelPackageDefinitionV2: universe.v4.model.PackageDefinition = MinimalV3ModelV2PackageDefinition
  val MaximalV3ModelPackageDefinitionV3: universe.v4.model.PackageDefinition = MaximalV3ModelV3PackageDefinition
  val MinimalV3ModelPackageDefinitionV3: universe.v4.model.PackageDefinition = MinimalV3ModelV3PackageDefinition

  val MaximalV2Resource = universe.v2.model.Resource(
    assets = Some(universe.v2.model.Assets(
      uris = Some(Map(
        "foo.tar.gz" -> "http://mesosphere.com/foo.tar.gz",
        "bar.jar"    -> "https://mesosphere.com/bar.jar"
      )),
      container = Some(universe.v2.model.Container(Map(
        "image1" -> "docker/image:1",
        "image2" -> "docker/image:2"
      )))
    )),
    images = Some(universe.v2.model.Images(
      iconSmall,
      iconMedium,
      iconLarge,
      screenshots
    ))
  )

  val MaximalV3ModelMetadata = universe.v3.model.V3Metadata(
    PackagingVersion,
    Name,
    Version,
    Maintainer,
    Description,
    Tags,
    Scm,
    Website,
    Framework,
    PreInstallNotes,
    PostInstallNotes,
    PostUninstallNotes,
    Licenses,
    MinDcosReleaseVersion,
    MarathonTemplate,
    Resource,
    Config
  )

  val MinimalV3ModelMetadata = universe.v3.model.V3Metadata(
    PackagingVersion,
    MinimalName,
    Version,
    Maintainer,
    Description
  )

  val HelloWorldMarathonTemplate: ByteBuffer = {
    val templateText =
      """
        |{
        |  "id": "helloworld",
        |  "cpus": 1.0,
        |  "mem": 512,
        |  "instances": 1,
        |  "cmd": "python3 -m http.server {{port}}",
        |  "container": {
        |    "type": "DOCKER",
        |    "docker": {
        |      "image": "python:3",
        |      "network": "HOST"
        |    }
        |  }
        |}
      """.stripMargin

    ByteBuffer.wrap(templateText.getBytes(StandardCharsets.UTF_8))
  }

  val HelloWorldV3Package: universe.v3.model.V3Package = universe.v3.model.V3Package(
    name = "helloworld",
    version = universe.v3.model.Version("0.1.0"),
    releaseVersion = MinReleaseVersion,
    website = Some("https://github.com/mesosphere/dcos-helloworld"),
    maintainer = "support@mesosphere.io",
    description = "Example DCOS application package",
    preInstallNotes = Some("A sample pre-installation message"),
    postInstallNotes = Some("A sample post-installation message"),
    tags = List("mesosphere", "example", "subcommand")
      .map(s => universe.v3.model.Tag(s)),
    marathon = Some(universe.v3.model.Marathon(HelloWorldMarathonTemplate)),
    config = Some(JsonObject.fromMap(Map(
      "$schema" -> "http://json-schema.org/schema#".asJson,
      "type" -> "object".asJson,
      "properties" -> Json.obj(
        "port" -> Json.obj(
          "type" -> "integer".asJson,
          "default" -> 8080.asJson
        )
      )
    )))
  )

  val MaximalV4ModelMetadata = universe.v4.model.V4Metadata(
    universe.v4.model.V4PackagingVersion,
    Name + "v4",
    Version,
    Maintainer,
    Description,
    Tags,
    Scm,
    Website,
    Framework,
    PreInstallNotes,
    PostInstallNotes,
    PostUninstallNotes,
    Licenses,
    MinDcosReleaseVersion,
    MarathonTemplate,
    Resource,
    Config,
    upgradesFrom = Some(List(universe.v3.model.ExactVersion(universe.v3.model.Version("8.0")))),
    downgradesTo = Some(List(universe.v3.model.ExactVersion(universe.v3.model.Version("8.0")))),
    lastUpdated=lastUpdated,
    hasKnownIssues=hasKnownIssues
  )

  val MinimalV4ModelMetadata = universe.v4.model.V4Metadata(
    universe.v4.model.V4PackagingVersion,
    MinimalName + "v4",
    Version,
    Maintainer,
    Description
  )

  val validPackagingVersions: TableFor2[universe.v4.model.PackagingVersion, String] = {
    new TableFor2(
      "PackagingVersion" -> "String",
      universe.v3.model.V2PackagingVersion -> "2.0",
      universe.v3.model.V3PackagingVersion -> "3.0",
      universe.v4.model.V4PackagingVersion -> "4.0",
      universe.v5.model.V5PackagingVersion -> "5.0"
    )
  }

  val versionStringList =  validPackagingVersions.map(_._2).mkString("[", ", ", "]")

  def renderInvalidVersionMessage(invalidVersion: String): String = {
    s"Expected one of $versionStringList for packaging version, but found [$invalidVersion]"
  }

  val MinimalV4ModelV4PackageDefinition: universe.v4.model.V4Package = universe.v4.model.V4Package(
    packagingVersion = universe.v4.model.V4PackagingVersion,
    name = "minimalv4",
    version = universe.v3.model.Version("1.2.3"),
    releaseVersion = universe.v3.model.ReleaseVersion(0),
    maintainer = "minimal@mesosphere.io",
    description = "A minimal package definition"
  )

  val MaximalV4ModelV4PackageDefinition: universe.v4.model.V4Package = universe.v4.model.V4Package(
    universe.v4.model.V4PackagingVersion,
    Name + "v4",
    Version,
    releaseVersion = universe.v3.model.ReleaseVersion(Long.MaxValue),
    Maintainer,
    Description,
    Tags,
    None,
    Scm,
    Website,
    Framework,
    PreInstallNotes,
    PostInstallNotes,
    PostUninstallNotes,
    Licenses,
    MinDcosReleaseVersion,
    MarathonTemplate,
    Resource,
    Config,
    upgradesFrom = Some(List(universe.v3.model.ExactVersion(universe.v3.model.Version("8.0")))),
    downgradesTo = Some(List(universe.v3.model.ExactVersion(universe.v3.model.Version("8.0")))),
    lastUpdated=lastUpdated,
    hasKnownIssues=hasKnownIssues
  )

  val MaximalV4ModelPackageDefinitionV4: universe.v4.model.PackageDefinition = MaximalV4ModelV4PackageDefinition
  val MinimalV4ModelPackageDefinitionV4: universe.v4.model.PackageDefinition = MinimalV4ModelV4PackageDefinition

  val supportedPackageDefinitions: TableFor1[universe.v4.model.SupportedPackageDefinition] =
    new TableFor1(
      "supportedPackageDefinition",
      MinimalV3ModelV3PackageDefinition,
      MaximalV3ModelV3PackageDefinition,
      MinimalV4ModelV4PackageDefinition,
      MaximalV4ModelV4PackageDefinition
    )

  val packageDefinitions: TableFor1[universe.v4.model.PackageDefinition] =
    new TableFor1(
      "packageDefinition",
      MinimalV3ModelV2PackageDefinition,
      MaximalV3ModelV2PackageDefinition,
      MinimalV3ModelV3PackageDefinition,
      MaximalV3ModelV3PackageDefinition,
      MinimalV4ModelV4PackageDefinition,
      MaximalV4ModelV4PackageDefinition
    )

}
