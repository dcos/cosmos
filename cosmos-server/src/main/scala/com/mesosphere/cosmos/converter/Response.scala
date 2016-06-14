package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.common.ByteBuffers
import com.twitter.bijection.Conversion.asMethod
import com.twitter.io.Charsets

object Response {

  def v3PackageToDescribeResponse(
    v3Package: universe.v3.model.V3Package
  ): rpc.v1.model.DescribeResponse = {
    rpc.v1.model.DescribeResponse(
      `package` = v3V3PackageToV2PackageDetails(v3Package),
      marathonMustache = v3Package.marathon match {
        case Some(marathon) =>
          new String(ByteBuffers.getBytes(marathon.v2AppMustacheTemplate), Charsets.Utf8)
        case _ =>
          ""
      },
      command = v3Package.command.as[Option[universe.v2.model.Command]],
      config = v3Package.config,
      resource = v3Package.resource.map(v3V3ResourceToV2Resource)
    )
  }

  def v2InstallResponseToV1InstallResponse(x: rpc.v2.model.InstallResponse): rpc.v1.model.InstallResponse = {
    rpc.v1.model.InstallResponse(
      packageName = x.packageName,
      packageVersion = x.packageVersion.as[universe.v2.model.PackageDetailsVersion],
      appId = x.appId
    )
  }

  private[this] def v3V3PackageToV2PackageDetails(
    v3Package: universe.v3.model.V3Package
  ): universe.v2.model.PackageDetails = {
    universe.v2.model.PackageDetails(
      packagingVersion = v3Package.packagingVersion.as[universe.v2.model.PackagingVersion],
      name = v3Package.name,
      version = v3Package.version.as[universe.v2.model.PackageDetailsVersion],
      maintainer = v3Package.maintainer,
      description = v3Package.description,
      tags = v3Package.tags.as[List[String]],
      selected = v3Package.selected,
      scm = v3Package.scm,
      website = v3Package.website,
      framework = v3Package.framework,
      preInstallNotes = v3Package.preInstallNotes,
      postInstallNotes = v3Package.postInstallNotes,
      postUninstallNotes = v3Package.postUninstallNotes,
      licenses = v3Package.licenses.as[Option[List[universe.v2.model.License]]]
    )
  }

}
