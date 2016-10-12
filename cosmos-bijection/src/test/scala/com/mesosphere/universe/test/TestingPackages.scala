package com.mesosphere.universe.test

import com.mesosphere.universe
import com.netaporter.uri.Uri
import io.circe.syntax._
import io.circe.JsonObject

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object TestingPackages {

  val MaximalV3ModelV3PackageDefinition: universe.v3.model.V3Package = universe.v3.model.V3Package(
    packagingVersion = universe.v3.model.V3PackagingVersion,
    name = "MAXIMAL",
    version = universe.v3.model.PackageDefinition.Version("9.87.654.3210"),
    releaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion(Int.MaxValue).get,
    maintainer = "max@mesosphere.io",
    description = "A complete package definition",
    marathon = Some(universe.v3.model.Marathon(
      v2AppMustacheTemplate = ByteBuffer.wrap("marathon template".getBytes(StandardCharsets.UTF_8))
    )),
    tags = List("all", "the", "things").map(s => universe.v3.model.PackageDefinition.Tag(s).get),
    selected = Some(true),
    scm = Some("git"),
    website = Some("mesosphere.com"),
    framework = Some(true),
    preInstallNotes = Some("pre-install message"),
    postInstallNotes = Some("post-install message"),
    postUninstallNotes = Some("post-uninstall message"),
    licenses = Some(List(
      universe.v3.model.License(name = "ABC", url = Uri.parse("http://foobar/a/b/c")),
      universe.v3.model.License(name = "XYZ", url = Uri.parse("http://foobar/x/y/z"))
    )),
    minDcosReleaseVersion = Some(universe.v3.model.DcosReleaseVersionParser.parseUnsafe("1.9.99")),
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

  val MinimalV3ModelV3PackageDefinition: universe.v3.model.V3Package = universe.v3.model.V3Package(
    packagingVersion = universe.v3.model.V3PackagingVersion,
    name = "minimal",
    version = universe.v3.model.PackageDefinition.Version("1.2.3"),
    releaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion(0).get,
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
        iconSmall = Some("small.png"),
        iconMedium = Some("medium.png"),
        iconLarge = Some("large.png"),
        screenshots = Some(List("ooh.png", "aah.png"))
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

  val MaximalV3ModelPackageDefinitionV2: universe.v3.model.PackageDefinition = MaximalV3ModelV2PackageDefinition
  val MinimalV3ModelPackageDefinitionV2: universe.v3.model.PackageDefinition = MinimalV3ModelV2PackageDefinition
  val MaximalV3ModelPackageDefinitionV3: universe.v3.model.PackageDefinition = MaximalV3ModelV3PackageDefinition
  val MinimalV3ModelPackageDefinitionV3: universe.v3.model.PackageDefinition = MinimalV3ModelV3PackageDefinition

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
      iconSmall = Some("small.png"),
      iconMedium = Some("medium.png"),
      iconLarge = Some("large.png"),
      screenshots = Some(List("ooh.png", "aah.png"))
    ))
  )

}
