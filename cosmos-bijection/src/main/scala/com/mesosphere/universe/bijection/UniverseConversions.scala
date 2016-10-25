package com.mesosphere.universe.bijection

import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.bijection.{Bijection, Conversion, Injection}
import com.twitter.bijection.Conversion.asMethod

import scala.util.{Failure, Success, Try}

object UniverseConversions {

  implicit val uriToString: Injection[Uri, String] = {
    Injection.build[Uri, String](_.toString)(s => Try(Uri.parse(s)))
  }

  implicit val v2PackagingVersionToString: Bijection[universe.v2.model.PackagingVersion, String] = {
    val fwd = (version: universe.v2.model.PackagingVersion) => version.toString
    val rev = universe.v2.model.PackagingVersion.apply _
    Bijection.build(fwd)(rev)
  }

  implicit val v3V2PackagingVersionToString: Injection[universe.v3.model.V2PackagingVersion.type, String] = {
    packagingVersionSubclassToString(universe.v3.model.V2PackagingVersion)
  }

  implicit val v3V3PackagingVersionToString: Injection[universe.v3.model.V3PackagingVersion.type, String] = {
    packagingVersionSubclassToString(universe.v3.model.V3PackagingVersion)
  }

  implicit val v3PackagingVersionToString: Injection[universe.v3.model.PackagingVersion, String] = {
    val fwd = (version: universe.v3.model.PackagingVersion) => version.show
    val rev = (universe.v3.model.PackagingVersion.apply _).andThen(BijectionUtils.twitterTryToScalaTry)

    Injection.build(fwd)(rev)
  }

  implicit val v3V2PackagingVersionToV2PackagingVersion:
    Injection[universe.v3.model.V2PackagingVersion.type, universe.v2.model.PackagingVersion] = {
    Injection.connect[universe.v3.model.V2PackagingVersion.type, String, universe.v2.model.PackagingVersion]
  }

  implicit val v3V3PackagingVersionToV2PackagingVersion:
    Injection[universe.v3.model.V3PackagingVersion.type, universe.v2.model.PackagingVersion] = {
    Injection.connect[universe.v3.model.V3PackagingVersion.type, String, universe.v2.model.PackagingVersion]
  }

  implicit val v3PackagingVersionToV2PackagingVersion:
    Injection[universe.v3.model.PackagingVersion, universe.v2.model.PackagingVersion] = {
    Injection.connect[universe.v3.model.PackagingVersion, String, universe.v2.model.PackagingVersion]
  }

  private[this] def packagingVersionSubclassToString[V <: universe.v3.model.PackagingVersion](
    expected: V
  ): Injection[V, String] = {
    val fwd = (version: V) => version.show
    val rev = (version: String) => {
      if (version == expected.show) {
        Success(expected)
      } else {
        val message =
          s"Expected value [${expected.show}] for packaging version, but found [$version]"
        Failure(new IllegalArgumentException(message))
      }
    }

    Injection.build(fwd)(rev)
  }

  implicit val v2ReleaseVersionToString: Bijection[universe.v2.model.ReleaseVersion, String] = {
    val fwd = (x: universe.v2.model.ReleaseVersion) => x.toString
    val rev = (x: String) => universe.v2.model.ReleaseVersion(x)
    Bijection.build(fwd)(rev)
  }

  implicit val v3ReleaseVersionToInt: Injection[universe.v3.model.PackageDefinition.ReleaseVersion, Int] = {
    val fwd = (x: universe.v3.model.PackageDefinition.ReleaseVersion) => x.value
    val rev = (universe.v3.model.PackageDefinition.ReleaseVersion.apply _).andThen(BijectionUtils.twitterTryToScalaTry)

    Injection.build(fwd)(rev)
  }

  implicit val v3ReleaseVersionToString:
    Injection[universe.v3.model.PackageDefinition.ReleaseVersion, String] = {
    Injection.connect[universe.v3.model.PackageDefinition.ReleaseVersion, Int, String]
  }

  implicit val v3ReleaseVersionToV2ReleaseVersion:
    Injection[universe.v3.model.PackageDefinition.ReleaseVersion,
      universe.v2.model.ReleaseVersion] = {
    Injection.connect[universe.v3.model.PackageDefinition.ReleaseVersion,
      String,
      universe.v2.model.ReleaseVersion]
  }


  implicit val v3PackageDefinitionToV2PackageDetails:
    Conversion[universe.v3.model.PackageDefinition, universe.v2.model.PackageDetails] = {
    Conversion.fromFunction {
      case v2Package: universe.v3.model.V2Package => v2Package.as[universe.v2.model.PackageDetails]
      case v3Package: universe.v3.model.V3Package => v3Package.as[universe.v2.model.PackageDetails]
    }
  }

  implicit val v3V3PackageToV2PackageDetails:
    Conversion[universe.v3.model.V3Package, universe.v2.model.PackageDetails] = {
    Conversion.fromFunction { (pkg: universe.v3.model.V3Package) =>
      universe.v2.model.PackageDetails(
        packagingVersion = pkg.packagingVersion.as[universe.v2.model.PackagingVersion],
        name = pkg.name,
        version = pkg.version.as[universe.v2.model.PackageDetailsVersion],
        maintainer = pkg.maintainer,
        description = pkg.description,
        tags = pkg.tags.as[List[String]],
        selected = pkg.selected,
        scm = pkg.scm,
        website = pkg.website,
        framework = pkg.framework,
        preInstallNotes = pkg.preInstallNotes,
        postInstallNotes = pkg.postInstallNotes,
        postUninstallNotes = pkg.postUninstallNotes,
        licenses = pkg.licenses.as[Option[List[universe.v2.model.License]]]
      )
    }
  }

  implicit val v3V2PackageToV2PackageDetails:
    Conversion[universe.v3.model.V2Package, universe.v2.model.PackageDetails] = {
    Conversion.fromFunction { (pkg: universe.v3.model.V2Package) =>
      universe.v2.model.PackageDetails(
        packagingVersion = pkg.packagingVersion.as[universe.v2.model.PackagingVersion],
        name = pkg.name,
        version = pkg.version.as[universe.v2.model.PackageDetailsVersion],
        maintainer = pkg.maintainer,
        description = pkg.description,
        tags = pkg.tags.as[List[String]],
        selected = pkg.selected,
        scm = pkg.scm,
        website = pkg.website,
        framework = pkg.framework,
        preInstallNotes = pkg.preInstallNotes,
        postInstallNotes = pkg.postInstallNotes,
        postUninstallNotes = pkg.postUninstallNotes,
        licenses = pkg.licenses.as[Option[List[universe.v2.model.License]]]
      )
    }
  }

  implicit val v2PackageDetailsVersionToV3PackageDefinitionVersion:
    Bijection[universe.v2.model.PackageDetailsVersion,
      universe.v3.model.PackageDefinition.Version] = {
    Bijection.build {
      x: universe.v2.model.PackageDetailsVersion => universe.v3.model.PackageDefinition.Version(x.toString)
    } {
      x: universe.v3.model.PackageDefinition.Version => universe.v2.model.PackageDetailsVersion(x.toString)
    }
  }

  implicit val v3PackageDefinitionTagToString:
    Injection[universe.v3.model.PackageDefinition.Tag,
      String /* "Tag" in universe.v2.model.PackageDefinition is only a String */] = {
    val fwd = (x: universe.v3.model.PackageDefinition.Tag) => x.value
    val rev = (universe.v3.model.PackageDefinition.Tag.apply _).andThen(BijectionUtils.twitterTryToScalaTry)

    Injection.build(fwd)(rev)
  }

  implicit val v3LicenseToV2License:
    Injection[universe.v3.model.License, universe.v2.model.License] = {

    val fwd = (x: universe.v3.model.License) =>
      universe.v2.model.License(
        name = x.name,
        url = x.url.toString
      )
    val rev = (x: universe.v2.model.License) => {
      Injection.invert[Uri, String](x.url).map { url =>
        universe.v3.model.License(name = x.name, url = url)
      }
    }

    Injection.build(fwd)(rev)
  }

  implicit val v2CommandToV3Command:
    Bijection[universe.v2.model.Command, universe.v3.model.Command] = {
    Bijection.build { (x: universe.v2.model.Command) =>
      universe.v3.model.Command(pip = x.pip)
    } { (x: universe.v3.model.Command) =>
      universe.v2.model.Command(pip = x.pip)
    }
  }

  implicit val v3V2ResourceToV3V3Resource:
    Injection[universe.v3.model.V2Resource, universe.v3.model.V3Resource] = {
    Injection.buildCatchInvert { (value: universe.v3.model.V2Resource) =>
      universe.v3.model.V3Resource(value.assets, value.images)
    } {
      case universe.v3.model.V3Resource(assets, images, Some(cli)) =>
        throw new IllegalArgumentException("Version 3 resource value contains a cli so cannot convert to version 2 Resource")
      case universe.v3.model.V3Resource(assets, images, None) =>
        universe.v3.model.V2Resource(assets, images)
    }
  }

  implicit val v2ResourceToV3V2Resource:
    Bijection[universe.v2.model.Resource, universe.v3.model.V2Resource] = {
    Bijection.build { (value: universe.v2.model.Resource) =>
      universe.v3.model.V2Resource(
        value.assets.as[Option[universe.v3.model.Assets]],
        value.images.as[Option[universe.v3.model.Images]])
    } { (value: universe.v3.model.V2Resource) =>
      universe.v2.model.Resource(
        value.assets.as[Option[universe.v2.model.Assets]],
        value.images.as[Option[universe.v2.model.Images]])
    }
  }

  implicit val v2ResourceToV3V3Resource:
    Injection[universe.v2.model.Resource, universe.v3.model.V3Resource] = {
    Injection
      .connect[universe.v2.model.Resource,
        universe.v3.model.V2Resource,
        universe.v3.model.V3Resource]
  }

  implicit val v2AssetsToV3Assets:
    Bijection[universe.v2.model.Assets, universe.v3.model.Assets] = {
    Bijection.build { (x: universe.v2.model.Assets) =>
      universe.v3.model.Assets(
        uris = x.uris,
        container = x.container.as[Option[universe.v3.model.Container]]
      )
    } { (x: universe.v3.model.Assets) =>
      universe.v2.model.Assets(
        uris = x.uris,
        container = x.container.as[Option[universe.v2.model.Container]]
      )
    }
  }

  implicit val v2ContainerToV3Container:
    Bijection[universe.v2.model.Container, universe.v3.model.Container] = {
    Bijection.build { (x: universe.v2.model.Container) =>
      universe.v3.model.Container(docker = x.docker)
    } { (x: universe.v3.model.Container) =>
      universe.v2.model.Container(docker = x.docker)
    }
  }

  implicit val v2ImagesToV3Images:
    Bijection[universe.v2.model.Images, universe.v3.model.Images] = {
    Bijection.build { (x: universe.v2.model.Images) =>
      universe.v3.model.Images(
        iconSmall = x.iconSmall,
        iconMedium = x.iconMedium,
        iconLarge = x.iconLarge,
        screenshots = x.screenshots
      )
    } { (x: universe.v3.model.Images) =>
      universe.v2.model.Images(
        iconSmall = x.iconSmall,
        iconMedium = x.iconMedium,
        iconLarge = x.iconLarge,
        screenshots = x.screenshots
      )
    }
  }

  implicit val v3ImagesToV2Images:
    Bijection[universe.v3.model.Images, universe.v2.model.Images] = v2ImagesToV3Images.inverse

}
