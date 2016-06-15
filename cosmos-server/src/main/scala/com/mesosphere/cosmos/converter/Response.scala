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

   private[this] def getV2ResourceFromPackageDefinition(
     packageDef: universe.v3.model.PackageDefinition
   ): Option[universe.v2.model.Resource] = {
      packageDef match {
        case v2Package: universe.v3.model.V2Package =>
          v2Package.resource.as[Option[universe.v2.model.Resource]]
        case v3Package: universe.v3.model.V3Package =>
          v3Package.resource.as[Option[universe.v2.model.Resource]]
    }
  }

  def v2ListResponseToV1ListResponse(x: rpc.v2.model.ListResponse): rpc.v1.model.ListResponse = {
    rpc.v1.model.ListResponse(
      packages = x.packages.map { y =>
        rpc.v1.model.Installation(
          appId = y.appId,
          packageInformation = rpc.v1.model.InstalledPackageInformation(
              packageDefinition = y.packageInformation.as[universe.v2.model.PackageDetails],
              resourceDefinition = getV2ResourceFromPackageDefinition(y.packageInformation)
          )
        )
      }
    )
  }

}
