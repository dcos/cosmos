package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.CirceError
import com.mesosphere.cosmos.JsonSchemaMismatch
import com.mesosphere.cosmos.MarathonTemplateMustBeJsonObject
import com.mesosphere.cosmos.PackageAlreadyInstalled
import com.mesosphere.cosmos.PackageRunner
import com.mesosphere.cosmos.ServiceAlreadyStarted
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.render._
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future
import io.circe.syntax._
import scala.util.Left
import scala.util.Right

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
          case Right(renderedMarathonJson) =>
            packageRunner.launch(renderedMarathonJson)
              .map { runnerResponse =>
                rpc.v2.model.InstallResponse(
                  packageName = pkg.name,
                  packageVersion = pkg.version,
                  appId = Some(runnerResponse.id),
                  postInstallNotes = pkg.postInstallNotes,
                  cli = pkg.cli
                )
              }.handle {
              case ServiceAlreadyStarted() =>
                throw PackageAlreadyInstalled()
            }
          case Left(OptionsValidationFailure(validationErrors)) =>
            Future.exception(JsonSchemaMismatch(validationErrors))
          case Left(InvalidLabelSchema(cause)) =>
            Future.exception(CirceError(cause))
          case Left(RenderedTemplateNotJson(cause)) =>
            Future.exception(CirceError(cause))
          case Left(RenderedTemplateNotJsonObject) =>
            Future.exception(MarathonTemplateMustBeJsonObject)
          case Left(OptionsNotAllowed) =>
            val error = Map("message" -> "No schema available to validate the provided options").asJson
            Future.exception(JsonSchemaMismatch(List(error)))
          case Left(MissingMarathonV2AppTemplate) =>
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

