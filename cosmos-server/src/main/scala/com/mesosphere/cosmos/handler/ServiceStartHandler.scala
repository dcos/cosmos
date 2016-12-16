package com.mesosphere.cosmos.handler

import _root_.io.circe.syntax._
import cats.data.Xor
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.internal.model.LocalPackageOrigin
import com.mesosphere.cosmos.render._
import com.mesosphere.cosmos.repository.LocalPackageCollection
import com.mesosphere.cosmos.rpc.v1.model.Installed
import com.mesosphere.cosmos.rpc.v1.model.LocalPackage
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.util.Future

private[cosmos] final class ServiceStartHandler(
  localPackageCollection: LocalPackageCollection,
  packageRunner: PackageRunner
) extends EndpointHandler[rpc.v1.model.ServiceStartRequest, rpc.v1.model.ServiceStartResponse] {

  private[this] def asPackageDefinition(
    localPackage: LocalPackage,
    packageName: String
  ): universe.v3.model.PackageDefinition = {
    localPackage match {
      case installed: Installed => installed.metadata
      case _ => throw PackageNotInstalled(packageName)
    }
  }

  override def apply(
    request: rpc.v1.model.ServiceStartRequest
  )(
    implicit session: RequestSession
  ): Future[rpc.v1.model.ServiceStartResponse] = {
    localPackageCollection.getInstalledPackage(request.packageName, request.packageVersion)
      .flatMap { localPackage =>
        val pkg = asPackageDefinition(localPackage, request.packageName)
        val renderResult = PackageDefinitionRenderer.renderMarathonV2App(
          LocalPackageOrigin.uri,
          pkg,
          request.options,
          marathonAppId = None
        )

        renderResult match {
          case Xor.Right(renderedMarathonJson) =>
            packageRunner.launch(renderedMarathonJson)
              .map { runnerResponse =>
                rpc.v1.model.ServiceStartResponse(
                  packageName = pkg.name,
                  packageVersion = pkg.version,
                  appId = Some(runnerResponse.id)
                )
              }
          case Xor.Left(MissingMarathonV2AppTemplate) =>
            Future {
              rpc.v1.model.ServiceStartResponse(
                packageName = pkg.name,
                packageVersion = pkg.version,
                appId = None
              )
            }
          case Xor.Left(OptionsValidationFailure(validationErrors)) =>
            Future.exception(JsonSchemaMismatch(validationErrors))
          case Xor.Left(InvalidLabelSchema(cause)) =>
            Future.exception(CirceError(cause))
          case Xor.Left(RenderedTemplateNotJson(cause)) =>
            Future.exception(CirceError(cause))
          case Xor.Left(RenderedTemplateNotJsonObject) =>
            Future.exception(MarathonTemplateMustBeJsonObject)
          case Xor.Left(OptionsNotAllowed) =>
            val message = "No schema available to validate the provided options"
            Future.exception(JsonSchemaMismatch(List(Map("message" -> message).asJson)))
        }
      }
  }
}
