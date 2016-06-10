package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.model.v1.DescribeResponse
import com.mesosphere.universe.common.ByteBuffers
import com.mesosphere.universe.v2.model.PackageDetails
import com.mesosphere.universe.v2.{model => v2}
import com.mesosphere.universe.v3.{model => v3}
import com.twitter.bijection.Conversion.asMethod
import com.twitter.io.Charsets

object Response {

  def v3PackageToDescribeResponse(v3Package: v3.V3Package): DescribeResponse = {
    DescribeResponse(
      `package` = v3V3PackageToV2PackageDetails(v3Package),
      marathonMustache = v3Package.marathon match {
        case Some(marathon) =>
          new String(ByteBuffers.getBytes(marathon.v2AppMustacheTemplate), Charsets.Utf8)
        case _ =>
          ""
      },
      command = v3Package.command.as[Option[v2.Command]],
      config = v3Package.config,
      resource = v3Package.resource.map(v3V3ResourceToV2Resource)
    )
  }

  private[this] def v3V3PackageToV2PackageDetails(v3Package: v3.V3Package): v2.PackageDetails = {
    PackageDetails(
      packagingVersion = v3Package.packagingVersion.as[v2.PackagingVersion],
      name = v3Package.name,
      version = v3Package.version.as[v2.PackageDetailsVersion],
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
      licenses = v3Package.licenses.as[Option[List[v2.License]]]
    )
  }

}
