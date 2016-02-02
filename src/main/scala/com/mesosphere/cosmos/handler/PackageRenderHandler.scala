package com.mesosphere.cosmos.handler

import java.io.{StringReader, StringWriter}
import java.util.Base64

import cats.data.Xor
import com.github.mustachejava.DefaultMustacheFactory
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.jsonschema.JsonSchemaValidation
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.model.mesos.master.MarathonApp
import com.mesosphere.cosmos.{JsonSchemaMismatch, PackageCache, PackageFileNotJson, PackageRunner}
import com.twitter.io.Charsets
import com.twitter.util.Future
import io.circe.parse.parse
import io.circe.{Encoder, Json, JsonObject}
import io.finch.DecodeRequest

private[cosmos] final class PackageRenderHandler(packageCache: PackageCache)
  (implicit bodyDecoder: DecodeRequest[RenderRequest], encoder: Encoder[RenderResponse])
  extends EndpointHandler[RenderRequest, RenderResponse] {

  val accepts = MediaTypes.RenderRequest
  val produces = MediaTypes.RenderResponse

  import PackageInstallHandler._

  override def apply(request: RenderRequest): Future[RenderResponse] = {
    packageCache
      .getPackageByPackageVersion(request.packageName, request.packageVersion)
      .map { packageFiles =>
        RenderResponse(renderMustacheTemplate(packageFiles, request.options))
      }
  }
}
