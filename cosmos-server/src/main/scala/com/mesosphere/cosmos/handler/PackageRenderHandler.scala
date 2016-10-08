package com.mesosphere.cosmos.handler

import cats.data.Xor
import com.mesosphere.cosmos.{CirceError, JsonSchemaMismatch, ServiceMarathonTemplateNotFound}
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.render._
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc.v1.model.{RenderRequest, RenderResponse}
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v3.model.PackageDefinition
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future
import io.circe.syntax._

private[cosmos] final class PackageRenderHandler(
  packageCache: PackageCollection
) extends EndpointHandler[RenderRequest, RenderResponse] {

  override def apply(request: RenderRequest)(implicit
    session: RequestSession
  ): Future[RenderResponse] = {
    packageCache
      .getPackageByPackageVersion(
        request.packageName,
        request.packageVersion.as[Option[universe.v3.model.PackageDefinition.Version]]
      )
      .flatMap { case (v3Package, uri) =>
        val pkg: PackageDefinition = ??? // TODO: Fix me
        val packageConfig =
          PackageDefinitionRenderer.renderMarathonV2App(uri, pkg, request.options, request.appId)

        packageConfig match {
          case Xor.Right(json) =>
            Future.value(RenderResponse(json))
          case Xor.Left(pdre) => pdre match {
            case OptionsValidationFailure(validationErrors) =>
              Future.exception(JsonSchemaMismatch(validationErrors))
            case InvalidLabelSchema(cause) => Future.exception(CirceError(cause))
            case RenderedTemplateNotJson(cause) => Future.exception(CirceError(cause))
            case OptionsNotAllowed =>
              val error = Map("message" -> "No schema available to validate the provided options").asJson
              Future.exception(JsonSchemaMismatch(List(error)))
            case MissingMarathonV2AppTemplate =>
              Future.exception(ServiceMarathonTemplateNotFound(pkg.name, pkg.version))
          }
        }
      }
  }

}
