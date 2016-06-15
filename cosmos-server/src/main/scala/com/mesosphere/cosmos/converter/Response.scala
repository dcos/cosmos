package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.internal
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import com.twitter.io.Charsets
import scala.util.Try

object Response {

  def v2InstallResponseToV1InstallResponse(x: rpc.v2.model.InstallResponse): rpc.v1.model.InstallResponse = {
    rpc.v1.model.InstallResponse(
      packageName = x.packageName,
      packageVersion = x.packageVersion.as[universe.v2.model.PackageDetailsVersion],
      appId = x.appId
    )
  }

  def packageDefinitionToDescribeResponse(
      packageDefinition: internal.model.PackageDefinition)
    : rpc.v1.model.DescribeResponse = {
    // TODO(version): We are throwing. Not sure this is the right thing
    packageDefinition
      .as[Try[universe.v3.model.V2Package]]
      .map { v2Package =>
        rpc.v1.model.DescribeResponse(
            `package` = v2Package.as[universe.v2.model.PackageDetails],
            marathonMustache =
              new String(universe.common.ByteBuffers
                           .getBytes(v2Package.marathon.v2AppMustacheTemplate),
                         Charsets.Utf8),
            command = v2Package.command.as[Option[universe.v2.model.Command]],
            config = v2Package.config,
            resource =
              v2Package.resource.as[Option[universe.v2.model.Resource]]
        )
      }
      .get
  }
}
