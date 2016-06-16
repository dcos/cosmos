package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.PackageFileMissing
import com.mesosphere.cosmos.internal
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.{Bijection, Injection}
import java.nio.ByteBuffer
import scala.util.Failure
import scala.util.Try

object Universe {

  implicit val v2PackagingVersionToV3V2PackagingVersion: Bijection[
      universe.v2.model.PackagingVersion,
      universe.v3.model.V2PackagingVersion] = {

    Bijection.build { (_: universe.v2.model.PackagingVersion) =>
      universe.v3.model.V2PackagingVersion.instance
    } { (x: universe.v3.model.V2PackagingVersion) =>
      universe.v2.model.PackagingVersion(x.v)
    }
  }

  implicit val v3V2PackageDetailsToPackageDefinition: Bijection[
      universe.v2.model.PackagingVersion,
      universe.v3.model.V3PackagingVersion] = {

    Bijection.build { (_: universe.v2.model.PackagingVersion) =>
      universe.v3.model.V3PackagingVersion.instance
    } { (x: universe.v3.model.V3PackagingVersion) =>
      universe.v2.model.PackagingVersion(x.v)
    }
  }

  implicit val v3V2PackagingVersionToPackageDefinition: Injection[
      universe.v3.model.PackageDefinition, universe.v2.model.PackageDetails] = {
    Injection.build { (value: universe.v3.model.PackageDefinition) =>
      value match {
        case v2Package: universe.v3.model.V2Package =>
          v2Package.as[universe.v2.model.PackageDetails]
        case v3Package: universe.v3.model.V3Package =>
          v3Package.as[universe.v2.model.PackageDetails]
      }
      } { _ =>
      Try(throw new IllegalArgumentException("Cannot convert version 3 package definition to version 2"))
    }
  }

  implicit val v3V3PackageToV2PackageDetails: Injection[
      universe.v3.model.V3Package, universe.v2.model.PackageDetails] = {
    Injection.build { (value: universe.v3.model.V3Package) =>
      universe.v2.model.PackageDetails(
          packagingVersion =
            value.packagingVersion.as[universe.v2.model.PackagingVersion],
          name = value.name,
          version = value.version.as[universe.v2.model.PackageDetailsVersion],
          maintainer = value.maintainer,
          description = value.description,
          tags = value.tags.as[List[String]],
          selected = value.selected,
          scm = value.scm,
          website = value.website,
          framework = value.framework,
          preInstallNotes = value.preInstallNotes,
          postInstallNotes = value.postInstallNotes,
          postUninstallNotes = value.postUninstallNotes,
          licenses = value.licenses.as[Option[List[universe.v2.model.License]]]
      )
    } { _ =>
      Try(throw new IllegalArgumentException(
              "Cannot convert version 2 package details to version 3 package"))
    }
  }

  implicit val v3V2PackageToV2PackageDetails: Injection[
      universe.v3.model.V2Package, universe.v2.model.PackageDetails] = {
    Injection.build { (value: universe.v3.model.V2Package) =>
      universe.v2.model.PackageDetails(
          packagingVersion =
            value.packagingVersion.as[universe.v2.model.PackagingVersion],
          name = value.name,
          version = value.version.as[universe.v2.model.PackageDetailsVersion],
          maintainer = value.maintainer,
          description = value.description,
          tags = value.tags.as[List[String]],
          selected = value.selected,
          scm = value.scm,
          website = value.website,
          framework = value.framework,
          preInstallNotes = value.preInstallNotes,
          postInstallNotes = value.postInstallNotes,
          postUninstallNotes = value.postUninstallNotes,
          licenses = value.licenses.as[Option[List[universe.v2.model.License]]]
      )
    } { _ =>
      Try(throw new IllegalArgumentException(
              "Cannot convert version 2 package details to version 3 package"))
    }
  }

  implicit val v3V2PackageToInternalPackageDefinition: Injection[
      universe.v3.model.V2Package, internal.model.PackageDefinition] = {
    Injection.build { (value: universe.v3.model.V2Package) =>
      internal.model.PackageDefinition(
          value.packagingVersion,
          value.name,
          value.version,
          value.releaseVersion,
          value.maintainer,
          value.description,
          value.tags,
          value.selected,
          value.scm,
          value.website,
          value.framework,
          value.preInstallNotes,
          value.postInstallNotes,
          value.postUninstallNotes,
          value.licenses,
          None,
          Some(value.marathon),
          value.resource.as[Option[universe.v3.model.V3Resource]],
          value.config,
          value.command
      )
    } { (value: internal.model.PackageDefinition) =>
      // TODO(version): deal with minDcosReleaseVersion
      for {
        packagingVersion <- Try(value.packagingVersion.asInstanceOf[
                                   universe.v3.model.V2PackagingVersion])
        resource <- value.resource
                     .as[Try[Option[universe.v3.model.V2Resource]]]
        marathon <- Try(value.marathon.getOrElse(
                           throw PackageFileMissing("package.json")))
      } yield {
        universe.v3.model.V2Package(
            packagingVersion,
            value.name,
            value.version,
            value.releaseVersion,
            value.maintainer,
            value.description,
            marathon,
            value.tags,
            value.selected,
            value.scm,
            value.website,
            value.framework,
            value.preInstallNotes,
            value.postInstallNotes,
            value.postUninstallNotes,
            value.licenses,
            resource,
            value.config,
            value.command
        )
      }
    }
  }

  implicit val v3V3PackageToInternalPackageDefinition: Injection[
      universe.v3.model.V3Package, internal.model.PackageDefinition] = {
    Injection.build { (value: universe.v3.model.V3Package) =>
      internal.model.PackageDefinition(
          value.packagingVersion,
          value.name,
          value.version,
          value.releaseVersion,
          value.maintainer,
          value.description,
          value.tags,
          value.selected,
          value.scm,
          value.website,
          value.framework,
          value.preInstallNotes,
          value.postInstallNotes,
          value.postUninstallNotes,
          value.licenses,
          value.minDcosReleaseVersion,
          value.marathon,
          value.resource,
          value.config,
          value.command
      )
    } { (value: internal.model.PackageDefinition) =>
      Try(value.packagingVersion
            .asInstanceOf[universe.v3.model.V3PackagingVersion]).map {
        packagingVersion =>
          universe.v3.model.V3Package(
              packagingVersion,
              value.name,
              value.version,
              value.releaseVersion,
              value.maintainer,
              value.description,
              value.tags,
              value.selected,
              value.scm,
              value.website,
              value.framework,
              value.preInstallNotes,
              value.postInstallNotes,
              value.postUninstallNotes,
              value.licenses,
              value.minDcosReleaseVersion,
              value.marathon,
              value.resource,
              value.config,
              value.command
          )
      }
    }
  }

  // TODO(version): Can we write this as an Bijection?
  implicit val v3PackageDefinitionToInternalPackageDefinition: Injection[
      universe.v3.model.PackageDefinition,
      internal.model.PackageDefinition] = {
    Injection.build { (value: universe.v3.model.PackageDefinition) =>
      value match {
        case v2Package: universe.v3.model.V2Package =>
          v2Package.as[internal.model.PackageDefinition]
        case v3Package: universe.v3.model.V3Package =>
          v3Package.as[internal.model.PackageDefinition]
      }
    } { (value: internal.model.PackageDefinition) =>
      value.as[Try[universe.v3.model.V3Package]].orElse {
        value.as[Try[universe.v3.model.V2Package]]
      }
    }
  }

  implicit val v2PackageDetailsVersionToV3PackageDefinitionVersion: Bijection[
      universe.v2.model.PackageDetailsVersion,
      universe.v3.model.PackageDefinition.Version] = {
    Bijection.build { (x: universe.v2.model.PackageDetailsVersion) =>
      universe.v3.model.PackageDefinition.Version(x.toString)
    } { (x: universe.v3.model.PackageDefinition.Version) =>
      universe.v2.model.PackageDetailsVersion(x.toString)
    }
  }

  // TODO(version): Make this a Bijection and reverse
  implicit val v3PackageDefinitionTagToString: Injection[
      universe.v3.model.PackageDefinition.Tag, String] = {

    val fwd = (x: universe.v3.model.PackageDefinition.Tag) => x.value
    val rev = (x: String) => Try(universe.v3.model.PackageDefinition.Tag(x))

    Injection.build(fwd)(rev)
  }

  // TODO(version): Make this a Bijection and reverse
  implicit val v3LicenseToV2License: Injection[
      universe.v3.model.License, universe.v2.model.License] = {

    val fwd = (x: universe.v3.model.License) =>
      universe.v2.model.License(
          name = x.name,
          url = x.url.toString
    )
    val rev = (x: universe.v2.model.License) => {
      Common.uriToString.invert(x.url).map { url =>
        universe.v3.model.License(name = x.name, url = url)
      }
    }

    Injection.build(fwd)(rev)
  }

  implicit val v2CommandToV3Command: Bijection[
      universe.v2.model.Command, universe.v3.model.Command] = {
    Bijection.build { (x: universe.v2.model.Command) =>
      universe.v3.model.Command(pip = x.pip)
    } { (x: universe.v3.model.Command) =>
      universe.v2.model.Command(pip = x.pip)
    }
  }

  implicit val v3V3ResourceToV2Resource: Injection[
      universe.v3.model.V3Resource, universe.v2.model.Resource] = {
    Injection.build { (value: universe.v3.model.V3Resource) =>
      universe.v2.model.Resource(
        value.assets.as[Option[universe.v2.model.Assets]],
        value.images.as[Option[universe.v2.model.Images]]
      )
    } { _ =>
      Try(throw new IllegalArgumentException(
              "Cannot convert version 3 resource to v2 resource"))
    }
  }

  implicit val v3V2ResourceToV3V3Resource: Injection[
      universe.v3.model.V2Resource, universe.v3.model.V3Resource] = {
    Injection.build { (value: universe.v3.model.V2Resource) =>
      universe.v3.model.V3Resource(value.assets, value.images)
    } { (value: universe.v3.model.V3Resource) =>
      value.cli.foldLeft(Try[Unit](())) { (_, cli) =>
        Failure(new IllegalArgumentException(
                "Version 3 resource value contains a cli so cannot convert to version 2 Resource"))
      } map { _ =>
        universe.v3.model.V2Resource(value.assets, value.images)
      }
    }
  }

  implicit def v2ResourceToV3V2Resource: Bijection[
      universe.v2.model.Resource, universe.v3.model.V2Resource] = {
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

  implicit val v2AssetsToV3Assets: Bijection[
      universe.v2.model.Assets, universe.v3.model.Assets] = {

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

  implicit val v2ContainerToV3Container: Bijection[
      universe.v2.model.Container, universe.v3.model.Container] = {

    Bijection.build { (x: universe.v2.model.Container) =>
      universe.v3.model.Container(docker = x.docker)
    } { (x: universe.v3.model.Container) =>
      universe.v2.model.Container(docker = x.docker)
    }
  }

  implicit val v2ImagesToV3Images: Bijection[
      universe.v2.model.Images, universe.v3.model.Images] = {

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

  implicit val v3ReleaseVersionToV2ReleaseVersion: Injection[
    universe.v3.model.PackageDefinition.ReleaseVersion, universe.v2.model.ReleaseVersion] = {

    val fwd = (x: universe.v3.model.PackageDefinition.ReleaseVersion) =>
      universe.v2.model.ReleaseVersion(x.value.toString)

    val rev = (x: universe.v2.model.ReleaseVersion) =>
      Try {
        val parsedVersion = x.toString.toInt
        universe.v3.model.PackageDefinition.ReleaseVersion(parsedVersion)
      }

    Injection.build(fwd)(rev)
  }

}
