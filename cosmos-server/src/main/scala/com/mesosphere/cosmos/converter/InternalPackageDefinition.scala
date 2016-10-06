package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.internal
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod

object InternalPackageDefinition {

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
