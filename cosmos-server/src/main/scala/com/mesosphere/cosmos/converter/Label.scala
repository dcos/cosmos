package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.internal
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.{Bijection, Conversion}

object Label {

  implicit val internalPackageDefinitionToLabelV1PackageMetadata: Conversion[
      internal.model.PackageDefinition,
      label.v1.model.PackageMetadata
    ] = Conversion.fromFunction { (x: internal.model.PackageDefinition) =>
      label.v1.model.PackageMetadata(
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
        licenses = x.licenses.as[Option[List[universe.v2.model.License]]],
        images = x.resource.flatMap(_.images).as[Option[universe.v2.model.Images]]
      )
    }

  implicit val labelV1PackageMetadataToRpcV1InstalledPackageInformation: Bijection[
      label.v1.model.PackageMetadata,
      rpc.v1.model.InstalledPackageInformation
    ] = Bijection.build(fwd)(rev)

  private[this] def fwd(x: label.v1.model.PackageMetadata) = {
    rpc.v1.model.InstalledPackageInformation(
      packageDefinition = x,
      resourceDefinition = x.images.map { images =>
        universe.v2.model.Resource(images = Some(images))
      }
    )
  }

  private[this] def rev(x: rpc.v1.model.InstalledPackageInformation) = {
    label.v1.model.PackageMetadata(
      packagingVersion = x.packageDefinition.packagingVersion,
      name = x.packageDefinition.name,
      version = x.packageDefinition.version,
      maintainer = x.packageDefinition.maintainer,
      description = x.packageDefinition.description,
      tags = x.packageDefinition.tags,
      selected = x.packageDefinition.selected,
      scm = x.packageDefinition.scm,
      website = x.packageDefinition.website,
      framework = x.packageDefinition.framework,
      preInstallNotes = x.packageDefinition.preInstallNotes,
      postInstallNotes = x.packageDefinition.postInstallNotes,
      postUninstallNotes = x.packageDefinition.postUninstallNotes,
      licenses = x.packageDefinition.licenses,
      images = x.resourceDefinition.flatMap(_.images)
    )
  }

}
