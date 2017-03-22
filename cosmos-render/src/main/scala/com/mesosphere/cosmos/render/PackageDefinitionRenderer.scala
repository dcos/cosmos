package com.mesosphere.cosmos.render

import cats.syntax.either._
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.github.mustachejava.DefaultMustacheFactory
import com.mesosphere.cosmos.jsonschema.JsonSchema
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders.encodeAppId
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import com.mesosphere.universe.common.ByteBuffers
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import io.circe.Json
import io.circe.JsonObject
import io.circe.jawn.parse
import io.circe.syntax._
import java.io.StringReader
import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.util.Either
import scala.util.Left
import scala.util.Right


object PackageDefinitionRenderer {
  private[this] final val MustacheFactory = new DefaultMustacheFactory()
  private[this] implicit val jsf: JsonSchemaFactory = JsonSchemaFactory.byDefault()

  def renderMarathonV2App(
    sourceUri: Uri,
    pkgDef: universe.v3.model.PackageDefinition,
    options: Option[JsonObject],
    marathonAppId: Option[AppId]
  ): Either[PackageDefinitionRenderError, Json] = {
    pkgDef.marathon match {
      case None => Left(MissingMarathonV2AppTemplate)
      case Some(m) =>
        val defaultOptions = pkgDef.config.map(JsonSchema.extractDefaultsFromSchema)
        val defaultOptionsAndUserOptions = Seq(
          defaultOptions,
          options
        ).flatten.foldLeft(JsonObject.empty)(merge)

        validateOptionsAgainstSchema(pkgDef, defaultOptionsAndUserOptions).flatMap { _ =>
          /* now that we know the users options are valid for the schema, we build up a composite
           * json object to send into the mustache context for rendering. The following seq
           * prepares for the merge of all the options, documents at later indices have higher
           * priority than lower index objects order here is important, DO NOT carelessly re-order.
           */
          val mergedOptions = Seq(
            Some(defaultOptionsAndUserOptions),
            resourceJson(pkgDef)
          ).flatten.foldLeft(JsonObject.empty)(merge)

          renderTemplate(m.v2AppMustacheTemplate, mergedOptions).flatMap { mJson =>
            extractLabels(Json.fromJsonObject(mJson))
              .map { existingLabels =>
                val labels = MarathonLabels(pkgDef, sourceUri, defaultOptionsAndUserOptions)

                val newLabelsAndAppId = Seq(
                  Some(
                    JsonObject.singleton("labels", Json.fromJsonObject(labels.requiredLabelsJson))
                  ),
                  Some(
                    JsonObject.singleton("labels", Json.fromJsonObject(existingLabels))
                  ),
                  Some(
                    JsonObject.singleton(
                      "labels",
                      Json.fromJsonObject(labels.nonOverridableLabelsJson)
                    )
                  ),
                  marathonAppId.map(appIdDoc)
                ).flatten.foldLeft(JsonObject.empty)(merge)

                merge(mJson, newLabelsAndAppId)
              }
              .map(Json.fromJsonObject)
          }
        }
    }
  }

  private[this] def validateOptionsAgainstSchema(
    pkgDef: universe.v3.model.PackageDefinition,
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

  private[this] def resourceJson(
    pkgDef: universe.v3.model.PackageDefinition
  ): Option[JsonObject] = pkgDef match {
    case v2: universe.v3.model.V2Package =>
      v2.resource.map(res => JsonObject.singleton("resource", res.asJson))
    case v3: universe.v3.model.V3Package =>
      v3.resource.map(res => JsonObject.singleton("resource", res.asJson))
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

  private[this] def appIdDoc(appId: AppId): JsonObject = {
    JsonObject.singleton("id", appId.asJson)
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

  private[this] def extractLabels(obj: Json): Either[PackageDefinitionRenderError, JsonObject] = {
    val hasLabels = obj.cursor.fieldSet.exists(_.contains("labels"))
    if (hasLabels) {
      // this is a bit of a sketchy check since marathon could change underneath us, but we want to try and
      // surface this error to the user as soon as possible. The check that is being performed here is to ensure
      // that `.labels` of `obj` is a Map[String, String]. Marathon enforces this and produces a very cryptic
      // message if not adhered to, so we try and let the user know here where we can craft a more informational
      // error message.
      // If marathon ever changes its schema for labels then this code will most likely need a new version with
      // this version left intact for backward compatibility reasons.
      obj.cursor.get[Map[String, String]]("labels") match {
        case Left(err) => Left(InvalidLabelSchema(err))
        case Right(m) => Right(JsonObject.fromMap(m.mapValues(_.asJson)))
      }
    } else {
      Right(JsonObject.empty)
    }
  }

}
