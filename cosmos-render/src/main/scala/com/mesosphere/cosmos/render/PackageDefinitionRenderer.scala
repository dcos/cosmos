package com.mesosphere.cosmos.render

import cats.syntax.either._
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.github.mustachejava.DefaultMustacheFactory
import com.mesosphere.cosmos.bijection.CosmosConversions._
import com.mesosphere.cosmos.jsonschema.JsonSchema
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.label.v1.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders.encodeAppId
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.mesosphere.universe.common.ByteBuffers
import com.mesosphere.universe.common.JsonUtil
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import io.circe.Json
import io.circe.JsonObject
import io.circe.jawn.parse
import io.circe.syntax._
import java.io.StringReader
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64

// TODO: make sure that we test all of the cases tested in MarathonLabelsSpec

object PackageDefinitionRenderer {
  private[this] final val MustacheFactory = new DefaultMustacheFactory()
  private[this] implicit val jsf: JsonSchemaFactory = JsonSchemaFactory.byDefault()

  def renderMarathonV2App(
    sourceUri: Uri,
    pkgDef: universe.v4.model.PackageDefinition,
    options: Option[JsonObject],
    marathonAppId: Option[AppId]
  ): Either[PackageDefinitionRenderError, Json] = {
    pkgDef.marathon.map { marathon =>
      val defaultOptionsAndUserOptions = (
        pkgDef.config.map(JsonSchema.extractDefaultsFromSchema).toList ++ options.toList
      ).foldLeft(JsonObject.empty)(merge)

      validateOptionsAgainstSchema(pkgDef, defaultOptionsAndUserOptions).flatMap { _ =>
        /* Now that we know the users options are valid for the schema, we build up a composite
         * json object to send into the mustache context for rendering. The following seq
         * prepares for the merge of all the options, documents at later indices have higher
         * priority than lower index objects order here is important, DO NOT carelessly re-order.
         */
        val mergedOptions = (
          defaultOptionsAndUserOptions ::
          pkgDef.resourceJson.map(rj => JsonObject.singleton("resource", rj)).toList
        ).foldLeft(JsonObject.empty)(merge)

        renderTemplate(marathon.v2AppMustacheTemplate, mergedOptions).flatMap { mJson =>
          extractLabels(mJson.asJson).map { existingLabels =>
            Json.fromJsonObject(
              decorateMarathonJson(
                mJson,
                sourceUri,
                pkgDef,
                options,
                marathonAppId,
                existingLabels
              )
            )
          }
        }
      }
    } getOrElse Left(MissingMarathonV2AppTemplate)
  }

  // TODO: Write documentation for what this method is doing.
  private[this] def decorateMarathonJson(
    marathonJson: JsonObject,
    sourceUri: Uri,
    pkg: universe.v4.model.PackageDefinition,
    options: Option[JsonObject],
    marathonAppId: Option[AppId],
    existingLabels: Json
  ): JsonObject = {
    val requiredLabels = Json.fromFields(
      Map(
        (MarathonApp.metadataLabel, encodeForLabel(pkg.as[label.v1.model.PackageMetadata].asJson)),
        (MarathonApp.registryVersionLabel, pkg.packagingVersion.show),
        (MarathonApp.nameLabel, pkg.name),
        (MarathonApp.versionLabel, pkg.version.toString),
        (MarathonApp.repositoryLabel, sourceUri.toString),
        (MarathonApp.releaseLabel, pkg.releaseVersion.value.toString),
        (MarathonApp.isFrameworkLabel, pkg.framework.getOrElse(false).toString)
      ).mapValues(_.asJson)
    )

    val nonOverridableLabels = Json.fromFields(
      (
        (
          MarathonApp.optionsLabel,
          encodeForLabel(options.getOrElse(JsonObject.empty).asJson)
        ) ::
        pkg.command.map { command =>
          (
            MarathonApp.commandLabel,
            encodeForLabel(command.asJson(universe.v3.model.Command.encodeCommand))
          )
        }.toList
      ).toMap.mapValues(_.asJson)
    )

    (
      marathonAppId.map(id => JsonObject.singleton("id", id.asJson)).toList ++
      List(
        JsonObject.singleton("labels", requiredLabels),
        JsonObject.singleton("labels", existingLabels),
        JsonObject.singleton("labels", nonOverridableLabels)
      )
    ).foldLeft(marathonJson)(merge)
  }

  private[this] def validateOptionsAgainstSchema(
    pkgDef: universe.v4.model.PackageDefinition,
    options: JsonObject
  ): Either[PackageDefinitionRenderError, Unit] = {
    (pkgDef.config, options.nonEmpty) match {
      // Success scenarios
      case (None, false) => Right(())
      case (Some(_), false) => Right(())
      // Failure scenarios
      case (None, true) => Left(OptionsNotAllowed)
      case (Some(schema), true) =>
        JsonSchema.jsonObjectMatchesSchema(options, schema)
          .leftMap(OptionsValidationFailure)
    }
  }

  private[render] def merge(target: JsonObject, fragment: JsonObject): JsonObject = {
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

  private[this] def renderTemplate(
    template: ByteBuffer,
    context: JsonObject
  ): Either[PackageDefinitionRenderError, JsonObject] = {
    val renderedJsonString = {
      val templateString = new String(ByteBuffers.getBytes(template), StandardCharsets.UTF_8)
      val strReader = new StringReader(templateString)
      val mustache = MustacheFactory.compile(strReader, ".marathon.v2AppMustacheTemplate")
      val params = jsonToJava(Json.fromJsonObject(context))
      val output = new StringWriter()
      mustache.execute(output, params)
      output.toString
    }

    parse(renderedJsonString).map(_.asObject) match {
      case Left(pe)           => Left(RenderedTemplateNotJson(pe))
      case Right(None)        => Left(RenderedTemplateNotJsonObject)
      case Right(Some(obj))   => Right(obj)
    }
  }

  private[this] def jsonToJava(json: Json): Any = {
    import scala.collection.JavaConverters._
    json.fold(
      jsonNull = null,  // scalastyle:ignore null
      jsonBoolean = identity,
      jsonNumber = n => n.toInt.getOrElse(n.toDouble),
      jsonString = identity,
      jsonArray = _.map(jsonToJava).asJava,
      jsonObject = _.toMap.mapValues(jsonToJava).asJava
    )
  }

  private[this] def extractLabels(
    obj: Json
  ): Either[PackageDefinitionRenderError, Json] = {
    // This is a bit of a sketchy check since marathon could change underneath us, but we want
    // to try and surface this error to the user as soon as possible. The check that is being
    // performed here is to ensure that `.labels` of `obj` is a Map[String, String]. Marathon
    // enforces this and produces a very cryptic message if not adhered to, so we try and let
    // the user know here where we can craft a more informational error message.
    // If marathon ever changes its schema for labels then this code will most likely need a
    // new version with this version left intact for backward compatibility reasons.
    obj.cursor.getOrElse[Map[String, String]]("labels")(Map.empty) match {
      case Left(err) => Left(InvalidLabelSchema(err))
      case Right(labels) => Right(Json.fromFields(labels.mapValues(_.asJson)))
    }
  }

  private[this] def encodeForLabel(json: Json): String = {
    // TODO: Don't pretty print. Use noSpaces instead
    val bytes = JsonUtil.dropNullKeysPrinter.pretty(json).getBytes(StandardCharsets.UTF_8)
    Base64.getEncoder.encodeToString(bytes)
  }
}
