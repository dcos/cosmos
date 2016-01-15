package com.mesosphere.cosmos

import java.io.{StringReader, StringWriter}
import java.util.Base64

import cats.data.Xor
import com.github.mustachejava.DefaultMustacheFactory
import com.mesosphere.cosmos.model.{PackageFiles, Resource}
import com.twitter.io.Charsets
import io.circe.generic.auto._
import io.circe.parse.parse
import io.circe.syntax._
import io.circe.{Json, JsonObject}

import scala.collection.JavaConverters._

object PackageInstall {

  private[this] val MustacheFactory = new DefaultMustacheFactory()

  private[cosmos] def renderMustacheTemplate(
    packageFiles: PackageFiles,
    options: JsonObject
  ): CosmosResult[Json] = {
    val strReader = new StringReader(packageFiles.marathonJsonMustache)
    val mustache = MustacheFactory.compile(strReader, "marathon.json.mustache")

    val defaults = extractDefaultsFromConfig(packageFiles.configJson)
    val merged = merge(defaults, options)
    val resource = extractAssetsAsJson(packageFiles.resourceJson)
    val complete = merged + ("resource", Json.fromJsonObject(resource))
    val params = jsonToJava(Json.fromJsonObject(complete))

    val output = new StringWriter()
    mustache.execute(output, params)
    parse(output.toString)
      .leftMap(err => errorNel(PackageFileNotJson("marathon.json", err.message)))
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

  private[cosmos] def addLabels(
    marathonJson: Json,
    packageFiles: PackageFiles
  ): CosmosResult[Json] = {
    // add images to package.json metadata for backwards compatability in the UI
    val packageDef = packageFiles.packageJson.copy(images = packageFiles.resourceJson.images)

    // Circe populates omitted fields with null values; remove them (see GitHub issue #56)
    val packageJson = packageDef.asJson.mapObject { obj =>
      JsonObject.fromMap(obj.toMap.filterNot { case (k, v) => v.isNull })
    }
    val packageBytes = packageJson.noSpaces.getBytes(Charsets.Utf8)
    val packageMetadata = Base64.getEncoder.encodeToString(packageBytes)

    val commandBytes = packageFiles.commandJson.noSpaces.getBytes(Charsets.Utf8)
    val commandMetadata = Base64.getEncoder.encodeToString(commandBytes)

    val frameworkName = packageFiles.configJson.cursor
      .downField(packageDef.name)
      .flatMap(_.get[String]("framework-name").toOption)

    // insert labels
    val packageLabels: Map[String, String] = Seq(
      Some("DCOS_PACKAGE_METADATA" -> packageMetadata),
      Some("DCOS_PACKAGE_REGISTRY_VERSION" -> packageFiles.version),
      Some("DCOS_PACKAGE_NAME" -> packageDef.name),
      Some("DCOS_PACKAGE_VERSION" -> packageDef.version),
      Some("DCOS_PACKAGE_SOURCE" -> universeBundleUri().toString),
      Some("DCOS_PACKAGE_RELEASE" -> packageFiles.revision),
      Some("DCOS_PACKAGE_IS_FRAMEWORK" -> packageDef.framework.getOrElse(true).toString),
      Some("DCOS_PACKAGE_COMMAND" -> commandMetadata),
      frameworkName.map("PACKAGE_FRAMEWORK_NAME_KEY" -> _)
    ).flatten.toMap

    val existingLabels = marathonJson.cursor
      .get[Map[String, String]]("labels").getOrElse(Map.empty)

    Xor.Right(marathonJson.mapObject(_.+("labels", (existingLabels ++ packageLabels).asJson)))
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
