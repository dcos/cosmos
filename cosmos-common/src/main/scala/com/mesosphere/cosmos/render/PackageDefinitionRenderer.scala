package com.mesosphere.cosmos.render

import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.github.mustachejava.DefaultMustacheFactory
import com.mesosphere.cosmos.bijection.CosmosConversions._
import com.mesosphere.cosmos.circe.Decoders.convertToCosmosException
import com.mesosphere.cosmos.circe.Decoders.parse
import com.mesosphere.cosmos.error.JsonSchemaMismatch
import com.mesosphere.cosmos.error.MarathonTemplateMustBeJsonObject
import com.mesosphere.cosmos.error.OptionsNotAllowed
import com.mesosphere.cosmos.jsonschema.JsonSchema
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.model.StorageEnvelope
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.mesosphere.universe.common.ByteBuffers
import com.mesosphere.universe.common.JsonUtil
import com.mesosphere.universe.v4.model.PackageDefinition
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax._
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.Base64

object PackageDefinitionRenderer {
  private[this] final val MustacheFactory = new DefaultMustacheFactory {
    /* The encode method for DefaultMustacheFactory does HTML based encoding.
     * We are not generating HTML. This disables it and just passes the raw value along.
     */
    override def encode(value: String, writer: Writer): Unit = {
      writer.write(value)
    }
  }

  private[this] implicit val jsf: JsonSchemaFactory = JsonSchemaFactory.byDefault()

  def renderMarathonV2App(
    sourceUri: Uri,
    pkgDef: universe.v4.model.PackageDefinition,
    options: Option[JsonObject],
    marathonAppId: Option[AppId]
  ): Option[JsonObject] = {
    pkgDef.marathon.map { marathon =>
      val defaultOptionsAndUserOptions: JsonObject = mergeDefaultAndUserOptions(pkgDef, options)

      validateOptionsAgainstSchema(pkgDef, defaultOptionsAndUserOptions)
      /* Now that we know the users options are valid for the schema, we build up a composite
       * json object to send into the mustache context for rendering. The following seq
       * prepares for the merge of all the options, documents at later indices have higher
       * priority than lower index objects order here is important, DO NOT carelessly re-order.
       */
      val mergedOptions = (
        defaultOptionsAndUserOptions ::
        pkgDef.resourceJson.map(rj => JsonObject.singleton("resource", rj)).toList
      ).foldLeft(JsonObject.empty)(JsonUtil.merge)

      val mJson = renderTemplate(
        new String(ByteBuffers.getBytes(marathon.v2AppMustacheTemplate), StandardCharsets.UTF_8),
        mergedOptions
      )
      val existingLabels = extractLabels(mJson.asJson)
      decorateMarathonJson(
        mJson,
        sourceUri,
        pkgDef,
        options,
        marathonAppId,
        existingLabels
      )
    }
  }

  def mergeDefaultAndUserOptions(
    pkgDef: PackageDefinition,
    userSuppliedOptions: Option[JsonObject]
  ): JsonObject = {
    (pkgDef.config.map(JsonSchema.extractDefaultsFromSchema).toList ++ userSuppliedOptions.toList)
      .foldLeft(JsonObject.empty)(JsonUtil.merge)
  }

  def renderTemplate(
    template: String,
    context: JsonObject
  ): JsonObject = {
    val renderedJsonString = {
      val strReader = new StringReader(template)
      val mustache = MustacheFactory.compile(strReader, ".marathon.v2AppMustacheTemplate")
      val params = jsonToJava(Json.fromJsonObject(context))
      val output = new StringWriter()
      mustache.execute(output, params)
      output.toString
    }

    parse(renderedJsonString).asObject match {
      case None => throw MarathonTemplateMustBeJsonObject.exception
      case Some(json) => json
    }
  }

  def encodeForLabel(json: Json): String = {
    val bytes = JsonUtil.dropNullKeysPrinter.pretty(json).getBytes(StandardCharsets.UTF_8)
    Base64.getEncoder.encodeToString(bytes)
  }


  private[this] def nonOverridableLabels(
    pkg: universe.v4.model.PackageDefinition,
    sourceUri: Uri,
    options: Option[JsonObject]
  ): Json = {
    Json.fromFields(
      Map(
        (MarathonApp.metadataLabel, encodeForLabel(pkg.as[label.v1.model.PackageMetadata].asJson)),
        (MarathonApp.nameLabel, pkg.name),
        (MarathonApp.versionLabel, pkg.version.toString),
        (MarathonApp.repositoryLabel, sourceUri.toString),
        (MarathonApp.optionsLabel, encodeForLabel(options.getOrElse(JsonObject.empty).asJson)),
        (MarathonApp.packageLabel, encodeForLabel(StorageEnvelope(pkg).asJson))
      ).mapValues(_.asJson)
    )
  }


  /** Decorate the Marathon AppDefinition with package specific information.
   *
   *  As part of the rendering process we need to override or guarantee that certain labels
   *  exists. This method add such labels to the AppDefinition and optionally overrides the
   *  application id.
   */
  private[this] def decorateMarathonJson(
    marathonJson: JsonObject,
    sourceUri: Uri,
    pkg: universe.v4.model.PackageDefinition,
    options: Option[JsonObject],
    marathonAppId: Option[AppId],
    existingLabels: Json
  ): JsonObject = {
    (
      marathonAppId.map(id => JsonObject.singleton("id", id.asJson)).toList ++
      List(
        JsonObject.singleton("labels", existingLabels),
        JsonObject.singleton("labels", nonOverridableLabels(pkg, sourceUri, options))
      )
    ).foldLeft(marathonJson)(JsonUtil.merge)
  }

  private[this] def validateOptionsAgainstSchema(
    pkgDef: universe.v4.model.PackageDefinition,
    options: JsonObject
  ): Unit = {
    (pkgDef.config, options.nonEmpty) match {
      // Success scenarios
      case (None, false) =>
      case (Some(_), false) =>
      // Failure scenarios
      case (None, true) => {
        throw OptionsNotAllowed().exception
      }
      case (Some(schema), true) =>
        JsonSchema.jsonObjectMatchesSchema(options, schema) match {
          case Left(validationErrors) => throw JsonSchemaMismatch(validationErrors).exception
          case Right(_) =>
        }
    }
  }

  private[this] def jsonToJava(json: Json): Any = {
    import scala.collection.JavaConverters._
    json.fold(
      jsonNull = null,  // scalastyle:ignore null
      jsonBoolean = identity,
      jsonNumber = n => n.toInt.getOrElse(n.toDouble),
      jsonString = value => {
        /* Encode the string using a JSON string encoding and remove the beginning and ending ".
         * The slicing operation always succeeds because the small JSON string is "".
         */
        val string = value.asJson.noSpaces
        string.slice(1, string.length - 1)
      },
      jsonArray = _.map(jsonToJava).asJava,
      jsonObject = _.toMap.mapValues(jsonToJava).asJava
    )
  }

  private[this] def extractLabels(
    obj: Json
  ): Json = {
    // This is a bit of a sketchy check since marathon could change underneath us, but we want
    // to try and surface this error to the user as soon as possible. The check that is being
    // performed here is to ensure that `.labels` of `obj` is a Map[String, String]. Marathon
    // enforces this and produces a very cryptic message if not adhered to, so we try and let
    // the user know here where we can craft a more informational error message.
    // If marathon ever changes its schema for labels then this code will most likely need a
    // new version with this version left intact for backward compatibility reasons.
    val labels = convertToCosmosException(
      obj.hcursor.getOrElse[Map[String, String]]("labels")(Map.empty),
      obj.noSpaces
    )
    Json.fromFields(labels.mapValues(_.asJson))
  }
}
