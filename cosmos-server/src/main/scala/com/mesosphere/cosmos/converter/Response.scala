package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.ConversionFromPackageToV2DescribeResponse
import com.mesosphere.cosmos.ServiceMarathonTemplateNotFound
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v3.model.V2Package
import com.mesosphere.universe.v3.model.V3Package
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.bijection.Bijection._
import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try

object Response {
  implicit val internalV2InstallResponseToV1InstallResponse:
    Conversion[rpc.v2.model.InstallResponse, Try[rpc.v1.model.InstallResponse]] = {
    Conversion.fromFunction { (x: rpc.v2.model.InstallResponse) =>
      Try(
        x.appId.getOrElse(
          throw ServiceMarathonTemplateNotFound(x.packageName, x.packageVersion).exception
        )
      ).map { appId =>
        rpc.v1.model.InstallResponse(
          packageName = x.packageName,
          packageVersion = x.packageVersion.as[universe.v2.model.PackageDetailsVersion],
          appId = appId
        )
      }
    }
  }

  implicit val v4PackageDefinitionToV2DescribeResponse:
    Conversion[universe.v4.model.PackageDefinition, Try[rpc.v2.model.DescribeResponse]] = {
    Conversion.fromFunction { (pkg: universe.v4.model.PackageDefinition) =>

      val packagingVersion: Try[universe.v3.model.PackagingVersion] =
        pkg match {
          case _: universe.v3.model.V2Package =>
            Return(universe.v3.model.V2PackagingVersion)
          case _: universe.v3.model.V3Package =>
            Return(universe.v3.model.V3PackagingVersion)
          case v4: universe.v4.model.V4Package if notUsesUpdates(v4) =>
            Return(universe.v3.model.V3PackagingVersion)
          case _ =>
            Throw(ConversionFromPackageToV2DescribeResponse().exception)
        }

      packagingVersion.map { packagingVersion =>
        rpc.v2.model.DescribeResponse(
          packagingVersion,
          pkg.name,
          pkg.version,
          pkg.maintainer,
          pkg.description,
          pkg.tags,
          pkg.selected.getOrElse(false),
          pkg.scm,
          pkg.website,
          pkg.framework.getOrElse(false),
          pkg.preInstallNotes,
          pkg.postInstallNotes,
          pkg.postUninstallNotes,
          pkg.licenses,
          pkg.minDcosReleaseVersion,
          pkg.marathon,
          pkg.v3Resource,
          pkg.config,
          pkg.command
        )
      }

    }
  }

  /// See comment for the v2Resource method for why this conversion should always succeed.
  implicit val v4PackageDefinitionToInstalledPackageInformation:
    Conversion[universe.v4.model.PackageDefinition,
      rpc.v1.model.InstalledPackageInformation] = {
    Conversion.fromFunction {
      case pkg: universe.v3.model.V2Package =>
        rpc.v1.model.InstalledPackageInformation(
          packageDefinition = rpc.v1.model.InstalledPackageInformationPackageDetails(
            packagingVersion = pkg.packagingVersion.as[universe.v2.model.PackagingVersion],
            name = pkg.name,
            version = pkg.version.as[universe.v2.model.PackageDetailsVersion],
            maintainer = pkg.maintainer,
            description = pkg.description,
            tags = pkg.tags.as[List[String]],
            selected = pkg.selected.orElse(Some(false)),
            scm = pkg.scm,
            website = pkg.website,
            framework = pkg.framework.orElse(Some(false)),
            preInstallNotes = pkg.preInstallNotes,
            postInstallNotes = pkg.postInstallNotes,
            postUninstallNotes = pkg.postUninstallNotes,
            licenses = pkg.licenses.as[Option[List[universe.v2.model.License]]]
          ),
          resourceDefinition = pkg.resource.map(_.as[universe.v2.model.Resource])
        )
      case pkg: universe.v3.model.V3Package =>
        rpc.v1.model.InstalledPackageInformation(
          packageDefinition = rpc.v1.model.InstalledPackageInformationPackageDetails(
            packagingVersion = pkg.packagingVersion.as[universe.v2.model.PackagingVersion],
            name = pkg.name,
            version = pkg.version.as[universe.v2.model.PackageDetailsVersion],
            maintainer = pkg.maintainer,
            description = pkg.description,
            tags = pkg.tags.as[List[String]],
            selected = pkg.selected.orElse(Some(false)),
            scm = pkg.scm,
            website = pkg.website,
            framework = pkg.framework.orElse(Some(false)),
            preInstallNotes = pkg.preInstallNotes,
            postInstallNotes = pkg.postInstallNotes,
            postUninstallNotes = pkg.postUninstallNotes,
            licenses = pkg.licenses.as[Option[List[universe.v2.model.License]]]
          ),
        resourceDefinition = v2Resource(pkg)
      )
      case pkg: universe.v4.model.V4Package =>
        rpc.v1.model.InstalledPackageInformation(
          packageDefinition = rpc.v1.model.InstalledPackageInformationPackageDetails(
            packagingVersion = pkg.packagingVersion.as[universe.v2.model.PackagingVersion],
            name = pkg.name,
            version = pkg.version.as[universe.v2.model.PackageDetailsVersion],
            maintainer = pkg.maintainer,
            description = pkg.description,
            tags = pkg.tags.as[List[String]],
            selected = pkg.selected.orElse(Some(false)),
            scm = pkg.scm,
            website = pkg.website,
            framework = pkg.framework.orElse(Some(false)),
            preInstallNotes = pkg.preInstallNotes,
            postInstallNotes = pkg.postInstallNotes,
            postUninstallNotes = pkg.postUninstallNotes,
            licenses = pkg.licenses.as[Option[List[universe.v2.model.License]]]
          ),
        resourceDefinition = v2Resource(pkg)
      )
    }
  }

  /** Extract a v2 resource object from a package.
   *
   *  This method drops information. This is okay for now because of the following reasons:
   *
   *  1. There is no way for us to return CLI information in the package/list RPC.
   *  2. We are going to deprecate the package/list RPC and replace it with service/list which
   *     returns a better data type.
   *  3. This conversion is only used for the package/list RPC and should not be used for anything
   *     else.
   */
  private[this] def v2Resource(
    pkg: universe.v4.model.PackageDefinition
  ): Option[universe.v2.model.Resource] = pkg match {
    case v2: V2Package =>
      v2.resource.map(_.as[universe.v2.model.Resource])
    case v3: V3Package =>
      v3.resource.map { resource =>
        universe.v2.model.Resource(
          resource.assets.as[Option[universe.v2.model.Assets]],
          resource.images.as[Option[universe.v2.model.Images]]
        )
      }
    case v4: universe.v4.model.V4Package =>
      v4.resource.map { resource =>
        universe.v2.model.Resource(
          resource.assets.as[Option[universe.v2.model.Assets]],
          resource.images.as[Option[universe.v2.model.Images]]
        )
      }
  }

  private[this] def notUsesUpdates(pkg: universe.v4.model.V4Package): Boolean = {
    (pkg.downgradesTo.toList ++ pkg.upgradesFrom.toList).flatten.isEmpty
  }

}
