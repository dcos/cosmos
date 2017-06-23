package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.MarathonPackageRunner
import com.mesosphere.cosmos.error.CirceError
import com.mesosphere.cosmos.error.JsonSchemaMismatch
import com.mesosphere.cosmos.error.MarathonTemplateMustBeJsonObject
import com.mesosphere.cosmos.error.PackageNotInstalled
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model.LocalPackageOrigin
import com.mesosphere.cosmos.render.InvalidLabelSchema
import com.mesosphere.cosmos.render.MissingMarathonV2AppTemplate
import com.mesosphere.cosmos.render.OptionsNotAllowed
import com.mesosphere.cosmos.render.OptionsValidationFailure
import com.mesosphere.cosmos.render.PackageDefinitionRenderer
import com.mesosphere.cosmos.render.RenderedTemplateNotJson
import com.mesosphere.cosmos.render.RenderedTemplateNotJsonObject
import com.mesosphere.cosmos.repository.LocalPackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.util.Future
import io.circe.syntax._

private[cosmos] final class ServiceStartHandler(
  localPackageCollection: LocalPackageCollection,
  packageRunner: MarathonPackageRunner
) extends EndpointHandler[rpc.v1.model.ServiceStartRequest, rpc.v1.model.ServiceStartResponse] {

  private[this] def asPackageDefinition(
    localPackage: rpc.v1.model.LocalPackage,
    packageName: String
  ): universe.v4.model.SupportedPackageDefinition = {
    localPackage match {
      case installed: rpc.v1.model.Installed => installed.metadata
      case _ => throw PackageNotInstalled(packageName).exception
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
          case Right(renderedMarathonJson) =>
            packageRunner.launch(renderedMarathonJson)
              .map { runnerResponse =>
                rpc.v1.model.ServiceStartResponse(
                  packageName = pkg.name,
                  packageVersion = pkg.version,
                  appId = Some(runnerResponse.id)
                )
              }
          case Left(MissingMarathonV2AppTemplate) =>
            Future {
              rpc.v1.model.ServiceStartResponse(
                packageName = pkg.name,
                packageVersion = pkg.version,
                appId = None
              )
            }
          case Left(OptionsValidationFailure(validationErrors)) =>
            Future.exception(JsonSchemaMismatch(validationErrors).exception)
          case Left(InvalidLabelSchema(cause)) =>
            Future.exception(CirceError(cause).exception)
          case Left(RenderedTemplateNotJson(cause)) =>
            Future.exception(CirceError(cause).exception)
          case Left(RenderedTemplateNotJsonObject) =>
            Future.exception(MarathonTemplateMustBeJsonObject.exception)
          case Left(OptionsNotAllowed) =>
            val message = "No schema available to validate the provided options"
            Future.exception(JsonSchemaMismatch(List(Map("message" -> message).asJson)).exception)
        }
      }
  }
}
