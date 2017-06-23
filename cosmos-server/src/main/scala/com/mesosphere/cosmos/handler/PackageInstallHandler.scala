package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.MarathonPackageRunner
import com.mesosphere.cosmos.error.CirceError
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.JsonSchemaMismatch
import com.mesosphere.cosmos.error.MarathonTemplateMustBeJsonObject
import com.mesosphere.cosmos.error.PackageAlreadyInstalled
import com.mesosphere.cosmos.error.ServiceAlreadyStarted
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.render.InvalidLabelSchema
import com.mesosphere.cosmos.render.MissingMarathonV2AppTemplate
import com.mesosphere.cosmos.render.OptionsNotAllowed
import com.mesosphere.cosmos.render.OptionsValidationFailure
import com.mesosphere.cosmos.render.PackageDefinitionRenderError
import com.mesosphere.cosmos.render.PackageDefinitionRenderer
import com.mesosphere.cosmos.render.RenderedTemplateNotJson
import com.mesosphere.cosmos.render.RenderedTemplateNotJsonObject
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future
import io.circe.JsonObject
import io.circe.syntax._

private[cosmos] final class PackageInstallHandler(
  packageCollection: PackageCollection,
  packageRunner: MarathonPackageRunner
) extends EndpointHandler[rpc.v1.model.InstallRequest, rpc.v2.model.InstallResponse] {

  override def apply(
    request: rpc.v1.model.InstallRequest
  )(
    implicit session: RequestSession
  ): Future[rpc.v2.model.InstallResponse] = {
    packageCollection
      .getPackageByPackageVersion(
        request.packageName,
        request.packageVersion.as[Option[universe.v3.model.Version]]
      )
      .flatMap { case (pkg, sourceUri) =>
        val packageConfig = PackageDefinitionRenderer.renderMarathonV2App(
          sourceUri,
          pkg,
          request.options,
          request.appId
        )

        handlePackageConfig(pkg, packageConfig)
      }
  }

  private[this] def handlePackageConfig(
    pkg: universe.v4.model.PackageDefinition,
    packageConfig: Either[PackageDefinitionRenderError, JsonObject]
  )(
    implicit session: RequestSession
  ): Future[rpc.v2.model.InstallResponse] = packageConfig match {
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
        }
        .handle {
          case CosmosException(ServiceAlreadyStarted(), _, _, _) =>
            throw PackageAlreadyInstalled().exception
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
      Future.exception(
        JsonSchemaMismatch(
          List(
            Map(
              "message" -> "No schema available to validate the provided options"
            ).asJson
          )
        ).exception
      )
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

