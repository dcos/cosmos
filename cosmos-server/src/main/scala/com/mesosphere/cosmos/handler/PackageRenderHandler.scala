package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.CirceError
import com.mesosphere.cosmos.JsonSchemaMismatch
import com.mesosphere.cosmos.MarathonTemplateMustBeJsonObject
import com.mesosphere.cosmos.ServiceMarathonTemplateNotFound
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.render._
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc.v1.model.{RenderRequest, RenderResponse}
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future
import io.circe.syntax._
import scala.util.Left
import scala.util.Right

private[cosmos] final class PackageRenderHandler(
  packageCache: PackageCollection
) extends EndpointHandler[RenderRequest, RenderResponse] {

  override def apply(request: RenderRequest)(implicit
    session: RequestSession
  ): Future[RenderResponse] = {
    packageCache
      .getPackageByPackageVersion(
        request.packageName,
        request.packageVersion.as[Option[universe.v3.model.Version]]
      )
      .flatMap { case (pkg, uri) =>
        val packageConfig =
          PackageDefinitionRenderer.renderMarathonV2App(uri, pkg, request.options, request.appId)

        packageConfig match {
          case Right(json) =>
            Future.value(RenderResponse(json))
          case Left(pdre) => pdre match {
            case OptionsValidationFailure(validationErrors) =>
              Future.exception(JsonSchemaMismatch(validationErrors).exception)
            case InvalidLabelSchema(cause) => Future.exception(CirceError(cause).exception)
            case RenderedTemplateNotJson(cause) => Future.exception(CirceError(cause).exception)
            case RenderedTemplateNotJsonObject =>
              Future.exception(MarathonTemplateMustBeJsonObject.exception)
            case OptionsNotAllowed =>
              val error = Map(
                "message" -> "No schema available to validate the provided options"
              ).asJson
              Future.exception(JsonSchemaMismatch(List(error)).exception)
            case MissingMarathonV2AppTemplate =>
              Future.exception(ServiceMarathonTemplateNotFound(pkg.name, pkg.version).exception)
          }
        }
      }
  }

}
