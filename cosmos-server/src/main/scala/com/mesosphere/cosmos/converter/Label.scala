package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.{label, rpc}
import com.mesosphere.universe
import com.twitter.bijection.Bijection

object Label {

  implicit val labelV1PackageMetadataToRpcV1RunningPackageInformation: Bijection[
      label.v1.model.PackageMetadata,
      rpc.v1.model.RunningPackageInformation
    ] = Bijection.build(fwd)(rev)

  private[this] def fwd(x: label.v1.model.PackageMetadata) = {
    rpc.v1.model.RunningPackageInformation(
      rpc.v1.model.RunningPackageInformationPackageDetails(
        packagingVersion = x.packagingVersion,
        name = x.name,
        version = x.version,
        maintainer = x.maintainer,
        description = x.description,
        tags = x.tags,
        selected = x.selected,
        scm = x.scm,
        website = x.website,
        framework = x.framework,
        preInstallNotes = x.preInstallNotes,
        postInstallNotes = x.postInstallNotes,
        postUninstallNotes = x.postUninstallNotes,
        licenses = x.licenses
      ),
      resourceDefinition = x.images.map(i => universe.v2.model.Resource(images = Some(i)))
    )
  }

  private[this] def rev(x: rpc.v1.model.RunningPackageInformation) = {
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
