package com.mesosphere.cosmos.handler

import cats.data.Xor
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.render._
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future
import io.circe.syntax._

private[cosmos] final class PackageInstallHandler(
  packageCollection: PackageCollection,
  packageRunner: PackageRunner
) extends EndpointHandler[rpc.v1.model.InstallRequest, rpc.v2.model.InstallResponse] {

  override def apply(request: rpc.v1.model.InstallRequest)(implicit
    session: RequestSession
  ): Future[rpc.v2.model.InstallResponse] = {
    packageCollection
      .getPackageByPackageVersion(
        request.packageName,
        request.packageVersion.as[Option[universe.v3.model.PackageDefinition.Version]]
      )
      .flatMap { case (pkg, sourceUri) =>
        val packageConfig =
          PackageDefinitionRenderer.renderMarathonV2App(sourceUri, pkg, request.options, request.appId)

        packageConfig match {
          case Xor.Right(renderedMarathonJson) =>
            packageRunner.launch(renderedMarathonJson)
              .map { runnerResponse =>
                rpc.v2.model.InstallResponse(
                  packageName = pkg.name,
                  packageVersion = pkg.version,
                  appId = Some(runnerResponse.id),
                  postInstallNotes = pkg.postInstallNotes,
                  cli = pkg.cli
                )
              }
          case Xor.Left(pdre) => pdre match {
            case OptionsValidationFailure(validationErrors) =>
              Future.exception(JsonSchemaMismatch(validationErrors))
            case InvalidLabelSchema(cause) => Future.exception(CirceError(cause))
            case RenderedTemplateNotJson(cause) => Future.exception(CirceError(cause))
            case RenderedTemplateNotJsonObject => Future.exception(MarathonTemplateMustBeJsonObject)
            case OptionsNotAllowed =>
              val error = Map("message" -> "No schema available to validate the provided options").asJson
              Future.exception(JsonSchemaMismatch(List(error)))
            case MissingMarathonV2AppTemplate =>
              Future {
                rpc.v2.model.InstallResponse(
                  packageName = pkg.name,
                  packageVersion = pkg.version,
                  appId = None,
                  postInstallNotes = pkg.postInstallNotes,
                  cli = pkg.cli
                )
              }
          }
        }
      }
  }

}

