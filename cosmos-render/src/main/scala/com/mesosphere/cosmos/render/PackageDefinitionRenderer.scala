package com.mesosphere.cosmos.render

import cats.data.Xor
import com.github.mustachejava.DefaultMustacheFactory
import com.mesosphere.cosmos.jsonschema.JsonSchema
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders.encodeAppId
import com.mesosphere.universe.common.ByteBuffers
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.universe.v3.model.{PackageDefinition, V2Package, V3Package}
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import io.circe.jawn.parse

import java.io.{StringReader, StringWriter}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets


object PackageDefinitionRenderer {
  private[this] final val MustacheFactory = new DefaultMustacheFactory()

  def renderMarathonV2App(
    sourceUri: Uri,
    pkgDef: PackageDefinition,
    options: Option[JsonObject],
    marathonAppId: Option[AppId]
  ): Xor[PackageDefinitionRenderError, Json] = {
    pkgDef.marathon match {
      case None => Xor.Left(MissingMarathonV2AppTemplate)
      case Some(m) =>
        val defaultOptions = pkgDef.config.map(JsonSchema.extractDefaultsFromSchema)
        val defaultOptionsAndUserOptions = Seq(defaultOptions, options).flatten.foldLeft(JsonObject.empty)(merge)
        validateOptionsAgainstSchema(pkgDef, defaultOptionsAndUserOptions).flatMap { _ =>
          // now that we know the users options are valid for the schema, we build up a composite json object
          // to send into the mustache context for rendering. The following seq prepares for the merge
          // of all the options, documents at later indices have higher priority than lower index objects
          // order here is important, DO NOT carelessly re-order.
          val mergedOptions = Seq(
            Some(defaultOptionsAndUserOptions),
            resourceJson(pkgDef)
          ).flatten.foldLeft(JsonObject.empty)(merge)

          renderTemplate(m.v2AppMustacheTemplate, mergedOptions).flatMap { mJson =>
            extractLabels(Json.fromJsonObject(mJson))
              .map { existingLabels =>
                val labels = MarathonLabels(pkgDef, sourceUri)

                val newLabelsAndAppId = Seq(
                  Some(labels.requiredLabelsJson).map(obj => JsonObject.singleton("labels", Json.fromJsonObject(obj))),
                  Some(existingLabels).map(obj => JsonObject.singleton("labels", Json.fromJsonObject(obj))),
                  Some(labels.nonOverridableLabelsJson).map(obj => JsonObject.singleton("labels", Json.fromJsonObject(obj))),
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
    pkgDef: PackageDefinition,
    options: JsonObject
  ): Xor[PackageDefinitionRenderError, Unit] = {
    (pkgDef.config, options.nonEmpty) match {
      // Success scenarios
      case (None, false) => Xor.Right(())
      case (Some(schema), false) => Xor.Right(())
      // Failure scenarios
      case (None, true) => Xor.Left(OptionsNotAllowed)
      case (Some(schema), true) =>
        JsonSchema.jsonObjectMatchesSchema(options, schema)
          .leftMap(OptionsValidationFailure)
    }
  }

  private[this] def resourceJson(pkgDef: PackageDefinition): Option[JsonObject] = pkgDef match {
    case v2: V2Package => v2.resource.map(res => JsonObject.singleton("resource", res.asJson))
    case v3: V3Package => v3.resource.map(res => JsonObject.singleton("resource", res.asJson))
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
  ): Xor[PackageDefinitionRenderError, JsonObject] = {
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
      case Xor.Left(pe)           => Xor.Left(RenderedTemplateNotJson(pe))
      case Xor.Right(None)        => Xor.Left(RenderedTemplateNotJson())
      case Xor.Right(Some(obj))   => Xor.Right(obj)
    }
  }

  private[this] def jsonToJava(json: Json): Any = {
    import scala.collection.JavaConverters._
    json.fold(
      jsonNull = null,
      jsonBoolean = identity,
      jsonNumber = n => n.toInt.getOrElse(n.toDouble),
      jsonString = identity,
      jsonArray = _.map(jsonToJava).asJava,
      jsonObject = _.toMap.mapValues(jsonToJava).asJava
    )
  }

  private[this] def extractLabels(obj: Json): Xor[PackageDefinitionRenderError, JsonObject] = {
    val hasLabels = obj.cursor.fieldSet.exists(_.contains("labels"))
    if (hasLabels) {
      // this is a bit of a sketchy check since marathon could change underneath us, but we want to try and
      // surface this error the user as soon as possible. The check that is being performed here is to ensure
      // that `.labels` of `obj` is a Map[String, String]. Marathon enforces this and produces a very cryptic
      // message if not adhered to, so we try and let the user know here were we can craft a more informational
      // error message.
      // If marathon ever changes it's schema for labels then this code will most likely need a new version with
      // this version left in tact for backward compatibility reasons.
      obj.cursor.get[Map[String, String]]("labels") match {
        case Xor.Left(err) => Xor.Left(InvalidLabelSchema(err))
        case Xor.Right(m) => Xor.Right(JsonObject.fromMap(m.mapValues(_.asJson)))
      }
    } else {
      Xor.right(JsonObject.empty)
    }
  }

}
