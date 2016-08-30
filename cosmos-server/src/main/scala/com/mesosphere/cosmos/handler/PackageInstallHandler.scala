package com.mesosphere.cosmos.handler

import java.io.{StringReader, StringWriter}

import cats.data.Xor
import com.github.mustachejava.DefaultMustacheFactory
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.jsonschema.JsonSchemaValidation
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import com.mesosphere.universe.common.ByteBuffers
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import com.twitter.io.Charsets
import com.twitter.util.Future
import io.circe.jawn.parse
import io.circe.syntax._
import io.circe.{Json, JsonObject}

import scala.collection.JavaConverters._

private[cosmos] final class PackageInstallHandler(
  packageCollection: PackageCollection,
  packageRunner: PackageRunner
) extends EndpointHandler[rpc.v1.model.InstallRequest, rpc.v2.model.InstallResponse] {

  import PackageInstallHandler._

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
          preparePackageConfig(request.appId, request.options, pkg, sourceUri)

        packageConfig match {
          case Some(pkgConfig) =>
            packageRunner.launch(pkgConfig)
              .map { runnerResponse =>
                rpc.v2.model.InstallResponse(
                  packageName = pkg.name,
                  packageVersion = pkg.version,
                  appId = Some(runnerResponse.id),
                  postInstallNotes = pkg.postInstallNotes,
                  cli = pkg.resource.flatMap(_.cli)
                )
              }
          case None =>
            Future {
              rpc.v2.model.InstallResponse(
                packageName = pkg.name,
                packageVersion = pkg.version,
                appId = None,
                postInstallNotes = pkg.postInstallNotes,
                cli = pkg.resource.flatMap(_.cli)
              )
            }
        }
      }
  }

}

object PackageInstallHandler {

  private final val MustacheFactory = new DefaultMustacheFactory()

  private[cosmos] def preparePackageConfig(
    appId: Option[AppId],
    options: Option[JsonObject],
    pkg: internal.model.PackageDefinition,
    sourceRepoUri: Uri
  ): Option[Json] = {
    pkg.marathon.map { marathon =>
      val packageConfig = pkg.config
      val assetsJson = pkg.resource
        .flatMap(_.assets)
        .map(_.asJson(universe.v3.circe.Encoders.encodeAssets))
      val mergedOptions = mergeOptions(packageConfig, assetsJson, options)
      val bytes = ByteBuffers.getBytes(marathon.v2AppMustacheTemplate)
      val marathonTemplate = new String(bytes, Charsets.Utf8)
      val marathonJson = renderMustacheTemplate(marathonTemplate, mergedOptions)

      val marathonLabels = MarathonLabels(pkg, sourceRepoUri)
      val marathonJsonWithLabels = addLabels(marathonJson, marathonLabels, mergedOptions)

      addAppId(marathonJsonWithLabels, appId)
    }
  }

  private final def mergeOptions(
    packageConfig: Option[JsonObject],
    assetsJson: Option[Json],
    options: Option[JsonObject]
  ): Json = {
    val defaults = extractDefaultsFromConfig(packageConfig)
    val merged: JsonObject = (packageConfig, options) match {
      case (None, None) => JsonObject.empty
      case (Some(config), None) => validConfig(defaults, config)
      case (None, Some(_)) =>
        val error = Map("message" -> "No schema available to validate the provided options").asJson
        throw JsonSchemaMismatch(List(error))
      case (Some(config), Some(opts)) =>
        val m = merge(defaults, opts)
        validConfig(m, config)
    }

    val complete = merged.add("resource", Json.obj("assets" -> assetsJson.getOrElse(Json.obj())))
    Json.fromJsonObject(complete)
  }

  private final def renderMustacheTemplate(
    template: String,
    mergedOptions: Json
  ): Json = {
    val strReader = new StringReader(template)
    val mustache = MustacheFactory.compile(strReader, "marathon.json.mustache")
    val params = jsonToJava(mergedOptions)

    val output = new StringWriter()
    mustache.execute(output, params)
    parse(output.toString) match {
      case Xor.Left(err) =>
        throw UnableToParseMarathonAsJson(err.message)
      case Xor.Right(rendered) => rendered
    }
  }

  private final def addLabels(
    marathonJson: Json,
    marathonLabels: MarathonLabels,
    mergedOptions: Json
  ): Json = {
    val hasLabels = marathonJson.cursor.fieldSet.exists(_.contains("labels"))
    val existingLabels = if (hasLabels) {
      marathonJson.cursor.get[Map[String, String]]("labels") match {
        case Xor.Left(df) => throw CirceError(df)
        case Xor.Right(labels) => labels
      }
    } else {
      Map.empty[String, String]
    }

    val packageLabels =
      marathonLabels.requiredLabels ++ existingLabels ++ marathonLabels.nonOverridableLabels

    marathonJson.mapObject(_.add("labels", packageLabels.asJson))
  }

  private final def addAppId(marathonJson: Json, appId: Option[AppId]): Json = {
    import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders.encodeAppId
    appId match {
      case Some(id) => marathonJson.mapObject(_.add("id", id.asJson))
      case _ => marathonJson
    }
  }

  private[this] def extractDefaultsFromConfig(configJson: Option[JsonObject]): JsonObject = {
    configJson
      .flatMap { json =>
        val topProperties =
          json("properties")
            .getOrElse(Json.Null)

        filterDefaults(topProperties)
          .asObject
      }
      .getOrElse(JsonObject.empty)
  }

  private[this] def filterDefaults(properties: Json): Json = {
    val defaults = properties
      .asObject
      .getOrElse(JsonObject.empty)
      .toMap
      .flatMap { case (propertyName, propertyJson) =>
        propertyJson
          .asObject
          .flatMap { propertyObject =>
            propertyObject("default").orElse {
              propertyObject("properties").map(filterDefaults)
            }
          }
          .map(propertyName -> _)
      }

    Json.fromJsonObject(JsonObject.fromMap(defaults))
  }

  private[this] def validConfig(options: JsonObject, config: JsonObject): JsonObject = {
    val validationErrors = JsonSchemaValidation.matchesSchema(options, config)
    if (validationErrors.nonEmpty) {
      throw JsonSchemaMismatch(validationErrors)
    }
    options
  }

  private[cosmos] def merge(target: JsonObject, fragment: JsonObject): JsonObject = {
    fragment.toList.foldLeft(target) { (updatedTarget, fragmentEntry) =>
      val (fragmentKey, fragmentValue) = fragmentEntry
      val targetValueOpt = updatedTarget(fragmentKey)

      val mergedValue = (targetValueOpt.flatMap(_.asObject), fragmentValue.asObject) match {
        case (Some(targetObject), Some(fragmentObject)) =>
          Json.fromJsonObject(merge(targetObject, fragmentObject))
        case _ => fragmentValue
      }

      updatedTarget.add(fragmentKey, mergedValue)
    }
  }

  private[this] def jsonToJava(json: Json): Any = {
    json.fold(
      jsonNull = null,
      jsonBoolean = identity,
      jsonNumber = n => n.toInt.getOrElse(n.toDouble),
      jsonString = identity,
      jsonArray = _.map(jsonToJava).asJava,
      jsonObject = _.toMap.mapValues(jsonToJava).asJava
    )
  }

}
