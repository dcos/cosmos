package com.mesosphere.cosmos.converter

import java.nio.charset.StandardCharsets
import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.{ServiceMarathonTemplateNotFound, internal, rpc}
import com.mesosphere.universe
import com.mesosphere.universe.common.ByteBuffers
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.Conversion
import com.twitter.util.Try

object Response {
  implicit val internalV2ResponseToV1Response: Conversion[
    rpc.v2.model.InstallResponse,
    Try[rpc.v1.model.InstallResponse]
  ] = Conversion.fromFunction { (x: rpc.v2.model.InstallResponse) =>
    Try(x.appId.getOrElse(throw ServiceMarathonTemplateNotFound(x.packageName, x.packageVersion))).map { appId =>
      rpc.v1.model.InstallResponse(
        packageName = x.packageName,
        packageVersion = x.packageVersion.as[universe.v2.model.PackageDetailsVersion],
        appId = appId
      )
    }
  }

  implicit val internalPackageDefinitionToDescribeResponse: Conversion[
    internal.model.PackageDefinition,
    Try[rpc.v1.model.DescribeResponse]
  ] = Conversion.fromFunction { (packageDefinition: internal.model.PackageDefinition) =>
    Try(packageDefinition.marathon.map(_.v2AppMustacheTemplate).getOrElse(
      throw ServiceMarathonTemplateNotFound(packageDefinition.name, packageDefinition.version)
    )).map { b64MarathonTemplate =>
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
        marathonMustache = new String(ByteBuffers.getBytes(b64MarathonTemplate), StandardCharsets.UTF_8),
        command = packageDefinition.command.as[Option[universe.v2.model.Command]],
        config = packageDefinition.config,
        resource = packageDefinition.resource.map(_.as[universe.v2.model.Resource])
      )
    }
  }
}
