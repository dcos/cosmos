package com.mesosphere.cosmos

import java.io.{StringReader, StringWriter}
import java.util.Base64

import cats.data.Xor
import com.github.mustachejava.DefaultMustacheFactory
import com.mesosphere.cosmos.jsonschema.JsonSchemaValidation
import com.mesosphere.cosmos.model.{InstallRequest, InstallResponse, PackageFiles, Resource}
import com.twitter.io.Charsets
import com.twitter.util.Future
import io.circe.generic.auto._
import io.circe.parse.parse
import io.circe.syntax._
import io.circe.{Json, JsonObject}

import scala.collection.JavaConverters._

object PackageInstall {

  private[this] val MustacheFactory = new DefaultMustacheFactory()

  private[cosmos] def install(packageCache: PackageCache, packageRunner: PackageRunner)(
    request: InstallRequest
  ): Future[InstallResponse] = {
    packageCache
      .getPackageFiles(request.packageName, request.packageVersion)
      .flatMap { packageFiles =>
        val packageConfig = preparePackageConfig(request, packageFiles)
        packageRunner
          .launch(packageConfig)
          .map { runnerResponse =>
            val packageName = packageFiles.packageJson.name
            val packageVersion = packageFiles.packageJson.version
            val appId = runnerResponse.id
            InstallResponse(packageName, packageVersion, appId)
          }
      }
  }

  private[this] def preparePackageConfig(
    request: InstallRequest,
    packageFiles: PackageFiles
  ): Json = {
    val marathonJson = renderMustacheTemplate(packageFiles, request.options.getOrElse(JsonObject.empty))
    val marathonJsonWithLabels = addLabels(marathonJson, packageFiles)

    request.appId match {
      case Some(id) => marathonJsonWithLabels.mapObject(_ + ("id", id.asJson))
      case _ => marathonJsonWithLabels
    }
  }

  private[this] def renderMustacheTemplate(
    packageFiles: PackageFiles,
    options: JsonObject
  ): Json = {
    val strReader = new StringReader(packageFiles.marathonJsonMustache)
    val mustache = MustacheFactory.compile(strReader, "marathon.json.mustache")

    val defaults = extractDefaultsFromConfig(packageFiles.configJson)
    val merged = merge(defaults, options)

    if (!JsonSchemaValidation.matchesSchema(Json.fromJsonObject(merged), packageFiles.configJson)) {
      throw JsonSchemaMismatch()
    }

    val resource = extractAssetsAsJson(packageFiles.resourceJson)
    val complete = merged + ("resource", Json.fromJsonObject(resource))
    val params = jsonToJava(Json.fromJsonObject(complete))

    val output = new StringWriter()
    mustache.execute(output, params)
    parse(output.toString) match {
      case Xor.Left(err) => throw PackageFileNotJson("marathon.json", err.message)
      case Xor.Right(rendered) => rendered
    }
  }

  private[this] def extractAssetsAsJson(resource: Resource): JsonObject = {
    val assets = resource.assets match {
      case Some(a) => a.asJson
      case _ => Json.obj()
    }

    JsonObject.singleton("assets", assets)
  }

  private[this] def extractDefaultsFromConfig(configJson: Json): JsonObject = {
    val topProperties = configJson
      .cursor
      .downField("properties")
      .map(_.focus)
      .getOrElse(Json.empty)

    filterDefaults(topProperties)
      .asObject
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

  private[this] def addLabels(
    marathonJson: Json,
    packageFiles: PackageFiles
  ): Json = {
    val packageMetadataJson = getPackageMetadataJson(packageFiles)
    val commandMetadataJson = packageFiles.commandJson

    val packageMetadata = encodeForLabel(packageMetadataJson)
    val commandMetadata = encodeForLabel(commandMetadataJson)

    val isFramework = packageFiles.packageJson.framework.getOrElse(true)

    val frameworkName = packageFiles.configJson.cursor
      .downField(packageFiles.packageJson.name)
      .flatMap(_.get[String]("framework-name").toOption)

    val requiredLabels: Map[String, String] = Map(
      "DCOS_PACKAGE_METADATA" -> packageMetadata,
      "DCOS_PACKAGE_REGISTRY_VERSION" -> packageFiles.version,
      "DCOS_PACKAGE_NAME" -> packageFiles.packageJson.name,
      "DCOS_PACKAGE_VERSION" -> packageFiles.packageJson.version,
      "DCOS_PACKAGE_SOURCE" -> packageFiles.sourceUri.toString,
      "DCOS_PACKAGE_RELEASE" -> packageFiles.revision,
      "DCOS_PACKAGE_IS_FRAMEWORK" -> isFramework.toString,
      "DCOS_PACKAGE_COMMAND" -> commandMetadata
    )

    val optionalLabels: Map[String, String] = Seq(
      frameworkName.map("PACKAGE_FRAMEWORK_NAME_KEY" -> _)
    ).flatten.toMap

    val existingLabels = marathonJson.cursor
      .get[Map[String, String]]("labels").getOrElse(Map.empty)

    val packageLabels = existingLabels ++ requiredLabels ++ optionalLabels

    marathonJson.mapObject(_ + ("labels", packageLabels.asJson))
  }

  private[this] def getPackageMetadataJson(packageFiles: PackageFiles): Json = {
    val packageJson = packageFiles.packageJson.asJson

    // add images to package.json metadata for backwards compatability in the UI
    val imagesJson = packageFiles.resourceJson.images.asJson
    val packageWithImages = packageJson.mapObject(_ + ("images", imagesJson))

    removeNulls(packageWithImages)
  }

  /** Circe populates omitted fields with null values; remove them (see GitHub issue #56) */
  private[this] def removeNulls(json: Json): Json = {
    json.mapObject { obj =>
      JsonObject.fromMap(obj.toMap.filterNot { case (k, v) => v.isNull })
    }
  }

  private[this] def encodeForLabel(json: Json): String = {
    val bytes = json.noSpaces.getBytes(Charsets.Utf8)
    Base64.getEncoder.encodeToString(bytes)
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

      updatedTarget + (fragmentKey, mergedValue)
    }
  }

}
