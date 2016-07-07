package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.ConversionFailure
import com.mesosphere.cosmos.internal
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.{Bijection, Conversion, Injection}

object Universe {

  implicit val v3V2PackagingVersionToV2PackagingVersion: Injection[
    universe.v3.model.V2PackagingVersion,
    universe.v2.model.PackagingVersion
    ] =
    Injection.buildCatchInvert {
      v: universe.v3.model.V2PackagingVersion => universe.v2.model.PackagingVersion(v.v)
    } {
      v: universe.v2.model.PackagingVersion => universe.v3.model.V2PackagingVersion(v.toString)
    }

  implicit val v3V3PackagingVersionToV2PackagingVersion: Injection[
    universe.v3.model.V3PackagingVersion,
    universe.v2.model.PackagingVersion
    ] =
    Injection.buildCatchInvert {
      v: universe.v3.model.V3PackagingVersion => universe.v2.model.PackagingVersion(v.v)
    } {
      v: universe.v2.model.PackagingVersion => universe.v3.model.V3PackagingVersion(v.toString)
    }

  implicit val v3PackagingVersionToV2PackagingVersion: Injection[
    universe.v3.model.PackagingVersion,
    universe.v2.model.PackagingVersion
    ] =
    Injection.buildCatchInvert { (x: universe.v3.model.PackagingVersion) => x match {
      case universe.v3.model.V2PackagingVersion(v) => universe.v2.model.PackagingVersion(v)
      case universe.v3.model.V3PackagingVersion(v) => universe.v2.model.PackagingVersion(v)
    }} {
      case universe.v2.model.PackagingVersion("2.0") => universe.v3.model.V2PackagingVersion.instance
      case universe.v2.model.PackagingVersion("3.0") => universe.v3.model.V3PackagingVersion.instance
      case v     => throw ConversionFailure(s"Supported packagingVersion: [2.0, 3.0] but was: '$v'")
    }

  implicit val v3PackageDefinitionToV2PackageDetails: Conversion[
    universe.v3.model.PackageDefinition,
    universe.v2.model.PackageDetails
    ] =
    Conversion.fromFunction {
      case v2Package: universe.v3.model.V2Package => v2Package.as[universe.v2.model.PackageDetails]
      case v3Package: universe.v3.model.V3Package => v3Package.as[universe.v2.model.PackageDetails]
    }

  implicit val v3V3PackageToV2PackageDetails: Conversion[
    universe.v3.model.V3Package,
    universe.v2.model.PackageDetails
    ] =
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

  implicit val v3V2PackageToV2PackageDetails: Conversion[
    universe.v3.model.V2Package,
    universe.v2.model.PackageDetails
    ] =
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

  implicit val v3V2PackageToInternalPackageDefinition: Conversion[
    universe.v3.model.V2Package,
    internal.model.PackageDefinition
    ] =
    Conversion.fromFunction { (value: universe.v3.model.V2Package) =>
      internal.model.PackageDefinition(
        value.packagingVersion,
        value.name,
        value.version,
        value.releaseVersion,
        value.maintainer,
        value.description,
        value.tags,
        value.selected.getOrElse(false),
        value.scm,
        value.website,
        value.framework.getOrElse(false),
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
    }

  implicit val v3V3PackageToInternalPackageDefinition: Conversion[
    universe.v3.model.V3Package,
    internal.model.PackageDefinition
    ] =
    Conversion.fromFunction { (value: universe.v3.model.V3Package) =>
      internal.model.PackageDefinition(
        value.packagingVersion,
        value.name,
        value.version,
        value.releaseVersion,
        value.maintainer,
        value.description,
        value.tags,
        value.selected.getOrElse(false),
        value.scm,
        value.website,
        value.framework.getOrElse(false),
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

  implicit val internalPackageDefinitionToV2DescribeResponse: Conversion[
    internal.model.PackageDefinition,
    rpc.v2.model.DescribeResponse
    ] =
    Conversion.fromFunction((pkgDef: internal.model.PackageDefinition) => {
      rpc.v2.model.DescribeResponse(
        pkgDef.packagingVersion,
        pkgDef.name,
        pkgDef.version,
        pkgDef.maintainer,
        pkgDef.description,
        pkgDef.tags,
        pkgDef.selected,
        pkgDef.scm,
        pkgDef.website,
        pkgDef.framework,
        pkgDef.preInstallNotes,
        pkgDef.postInstallNotes,
        pkgDef.postUninstallNotes,
        pkgDef.licenses,
        pkgDef.minDcosReleaseVersion,
        pkgDef.marathon,
        pkgDef.resource,
        pkgDef.config,
        pkgDef.command
      )
    })

  implicit val v3PackageDefinitionToInternalPackageDefinition: Conversion[
    universe.v3.model.PackageDefinition,
    internal.model.PackageDefinition
    ] =
    Conversion.fromFunction {
      case v2Package: universe.v3.model.V2Package => v2Package.as[internal.model.PackageDefinition]
      case v3Package: universe.v3.model.V3Package => v3Package.as[internal.model.PackageDefinition]
    }

  implicit val v2PackageDetailsVersionToV3PackageDefinitionVersion: Bijection[
    universe.v2.model.PackageDetailsVersion,
    universe.v3.model.PackageDefinition.Version
    ] =
    Bijection.build {
      x: universe.v2.model.PackageDetailsVersion => universe.v3.model.PackageDefinition.Version(x.toString)
    } {
      x: universe.v3.model.PackageDefinition.Version => universe.v2.model.PackageDetailsVersion(x.toString)
    }

  implicit val v3PackageDefinitionTagToString: Injection[
    universe.v3.model.PackageDefinition.Tag,
    String
    ] = {
    val fwd = (x: universe.v3.model.PackageDefinition.Tag) => x.value
    val rev = (x: String) => universe.v3.model.PackageDefinition.Tag(x)

    Injection.buildCatchInvert(fwd)(rev)
  }

  implicit val v3LicenseToV2License: Injection[
    universe.v3.model.License,
    universe.v2.model.License
    ] = {

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
    universe.v2.model.Command,
    universe.v3.model.Command
    ] = {
    Bijection.build { (x: universe.v2.model.Command) =>
      universe.v3.model.Command(pip = x.pip)
    } { (x: universe.v3.model.Command) =>
      universe.v2.model.Command(pip = x.pip)
    }
  }

  implicit val v3V3ResourceToV2Resource: Conversion[
    universe.v3.model.V3Resource,
    universe.v2.model.Resource
    ] = {
    Conversion.fromFunction { (value: universe.v3.model.V3Resource) =>
      universe.v2.model.Resource(
        value.assets.as[Option[universe.v2.model.Assets]],
        value.images.as[Option[universe.v2.model.Images]]
      )
    }
  }

  implicit val v3V2ResourceToV3V3Resource: Injection[
    universe.v3.model.V2Resource,
    universe.v3.model.V3Resource
    ] = {
    Injection.buildCatchInvert { (value: universe.v3.model.V2Resource) =>
      universe.v3.model.V3Resource(value.assets, value.images)
    } {
      case universe.v3.model.V3Resource(assets, images, Some(cli)) =>
        throw new IllegalArgumentException("Version 3 resource value contains a cli so cannot convert to version 2 Resource")
      case universe.v3.model.V3Resource(assets, images, None) =>
        universe.v3.model.V2Resource(assets, images)
    }
  }

  implicit val v2ResourceToV3V2Resource: Bijection[
    universe.v2.model.Resource,
    universe.v3.model.V2Resource
    ] =
    Bijection.build { (value: universe.v2.model.Resource) =>
      universe.v3.model.V2Resource(
        value.assets.as[Option[universe.v3.model.Assets]],
        value.images.as[Option[universe.v3.model.Images]])
    } { (value: universe.v3.model.V2Resource) =>
      universe.v2.model.Resource(
        value.assets.as[Option[universe.v2.model.Assets]],
        value.images.as[Option[universe.v2.model.Images]])
    }

  implicit val v2AssetsToV3Assets: Bijection[
    universe.v2.model.Assets,
    universe.v3.model.Assets
    ] =
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

  implicit val v2ContainerToV3Container: Bijection[
    universe.v2.model.Container,
    universe.v3.model.Container
    ] =
    Bijection.build { (x: universe.v2.model.Container) =>
      universe.v3.model.Container(docker = x.docker)
    } { (x: universe.v3.model.Container) =>
      universe.v2.model.Container(docker = x.docker)
    }

  implicit val v2ImagesToV3Images: Bijection[
    universe.v2.model.Images,
    universe.v3.model.Images
    ] =
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

  implicit val v3ImagesToV2Images: Bijection[
    universe.v3.model.Images,
    universe.v2.model.Images
    ] = v2ImagesToV3Images.inverse


  implicit val v3ReleaseVersionToV2ReleaseVersion: Injection[
    universe.v3.model.PackageDefinition.ReleaseVersion, universe.v2.model.ReleaseVersion] = {

    val fwd = (x: universe.v3.model.PackageDefinition.ReleaseVersion) =>
      universe.v2.model.ReleaseVersion(x.value.toString)

    val rev = (x: universe.v2.model.ReleaseVersion) =>
       {
        // TODO(version): This throws a generic parsing NumberFormatException
        val parsedVersion = x.toString.toInt
        universe.v3.model.PackageDefinition.ReleaseVersion(parsedVersion)
      }

    Injection.buildCatchInvert(fwd)(rev)
  }

  implicit val internalPackageDefinitionToInstalledPackageInformation: Conversion[
    internal.model.PackageDefinition,
    rpc.v1.model.InstalledPackageInformation
    ] =
    Conversion.fromFunction { (x: internal.model.PackageDefinition) =>
      rpc.v1.model.InstalledPackageInformation(
        packageDefinition = rpc.v1.model.InstalledPackageInformationPackageDetails(
          packagingVersion = x.packagingVersion.as[universe.v2.model.PackagingVersion],
          name = x.name,
          version = x.version.as[universe.v2.model.PackageDetailsVersion],
          maintainer = x.maintainer,
          description = x.description,
          tags = x.tags.as[List[String]],
          selected = Some(x.selected),
          scm = x.scm,
          website = x.website,
          framework = Some(x.framework),
          preInstallNotes = x.preInstallNotes,
          postInstallNotes = x.postInstallNotes,
          postUninstallNotes = x.postUninstallNotes,
          licenses = x.licenses.as[Option[List[universe.v2.model.License]]]
        ),
        resourceDefinition = x.resource.map(_.as[universe.v2.model.Resource])
      )

    }

}
