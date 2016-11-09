package com.mesosphere.cosmos.handler

import cats.data.Xor
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.render._
import com.mesosphere.cosmos.repository.LocalPackageCollection
import com.mesosphere.cosmos.rpc.v1.model.Installed
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future
import _root_.io.circe.syntax._

private[cosmos] final class ServiceStartHandler(
  localPackageCollection: LocalPackageCollection,
  packageRunner: PackageRunner
) extends EndpointHandler[rpc.v1.model.ServiceStartRequest, rpc.v2.model.ServiceStartResponse] {

  override def apply(
    request: rpc.v1.model.ServiceStartRequest
  )(
    implicit session: RequestSession
  ): Future[rpc.v2.model.ServiceStartResponse] = {
    localPackageCollection
      .getInstalledPackage(
        request.packageName,
        request.packageVersion.as[Option[universe.v3.model.PackageDefinition.Version]])
      .map(_.asInstanceOf[Installed].metadata -> Uri.parse("localPackageCollection"))
      .flatMap { case (pkg, sourceUri) =>
        val packageConfig =
          PackageDefinitionRenderer
            .renderMarathonV2App(sourceUri, pkg, request.options, request.appId)

        packageConfig match {
          case Xor.Right(renderedMarathonJson) =>
            packageRunner.launch(renderedMarathonJson)
              .map { runnerResponse =>
                rpc.v2.model.ServiceStartResponse(
                  packageName = pkg.name,
                  packageVersion = pkg.version,
                  appId = Some(runnerResponse.id)
                )
              }
          case Xor.Left(pdre) => pdre match {
            case OptionsValidationFailure(validationErrors) =>
              Future.exception(JsonSchemaMismatch(validationErrors))
            case InvalidLabelSchema(cause) =>
              Future.exception(CirceError(cause))
            case RenderedTemplateNotJson(cause) =>
              Future.exception(CirceError(cause))
            case RenderedTemplateNotJsonObject =>
              Future.exception(MarathonTemplateMustBeJsonObject)
            case OptionsNotAllowed =>
              val error = Map("message" -> "No schema available to validate the provided options").asJson
              Future.exception(JsonSchemaMismatch(List(error)))
            case MissingMarathonV2AppTemplate =>
              Future {
                rpc.v2.model.ServiceStartResponse(
                  packageName = pkg.name,
                  packageVersion = pkg.version,
                  appId = None
                )
              }
          }
        }
      }
  }

}

