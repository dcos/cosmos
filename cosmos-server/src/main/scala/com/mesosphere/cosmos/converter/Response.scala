package com.mesosphere.cosmos.converter

import java.nio.charset.StandardCharsets

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.ConversionFailure
import com.mesosphere.cosmos.internal
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.common.ByteBuffers
import com.twitter.bijection.Conversion.asMethod

object Response {

  def v2InstallResponseToV1InstallResponse(x: rpc.v2.model.InstallResponse): rpc.v1.model.InstallResponse = {
    rpc.v1.model.InstallResponse(
      packageName = x.packageName,
      packageVersion = x.packageVersion.as[universe.v2.model.PackageDetailsVersion],
      appId = x.appId
    )
  }

  def packageDefinitionToDescribeResponse(
    packageDefinition: internal.model.PackageDefinition
  ): rpc.v1.model.DescribeResponse = {
    rpc.v1.model.DescribeResponse(
      `package` = universe.v2.model.PackageDetails(
        packagingVersion = packageDefinition.packagingVersion.as[universe.v2.model.PackagingVersion],
        name = packageDefinition.name,
        version = packageDefinition.version.as[universe.v2.model.PackageDetailsVersion],
        maintainer = packageDefinition.maintainer,
        description = packageDefinition.description,
        tags = packageDefinition.tags.as[List[String]],
        selected = Some(packageDefinition.selected),
        scm = packageDefinition.scm,
        website = packageDefinition.website,
        framework = Some(packageDefinition.framework),
        preInstallNotes = packageDefinition.preInstallNotes,
        postInstallNotes = packageDefinition.postInstallNotes,
        postUninstallNotes = packageDefinition.postUninstallNotes,
        licenses = packageDefinition.licenses.as[Option[List[universe.v2.model.License]]]
      ),
      marathonMustache =
        internalPackageDefinitionMarathonToRpcV1DescribeResponseMarathon(packageDefinition.marathon),
      command = packageDefinition.command.as[Option[universe.v2.model.Command]],
      config = packageDefinition.config,
      resource = packageDefinition.resource.map(_.as[universe.v2.model.Resource])
    )
  }

  private[this] def internalPackageDefinitionMarathonToRpcV1DescribeResponseMarathon(
    v3Marathon: Option[universe.v3.model.Marathon]
  ): String = {
    v3Marathon match {
      case Some(marathon) =>
        new String(ByteBuffers.getBytes(marathon.v2AppMustacheTemplate), StandardCharsets.UTF_8)
      case _ =>
        throw ConversionFailure("Marathon template required")
    }
  }

}
