package com.mesosphere.cosmos.jsonschema

import cats.data.Xor
import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.mesosphere.cosmos.jsonschema.Jackson._
import io.circe.syntax._
import io.circe.{Json, JsonObject}

import scala.collection.JavaConverters._

private[cosmos] object JsonSchemaValidation {

  private[cosmos] def matchesSchema(document: JsonObject, schema: JsonObject): Iterable[Json] = {
    matchesSchema(Json.fromJsonObject(document), Json.fromJsonObject(schema))
  }

  private[cosmos] def matchesSchema(document: Json, schema: Json): Iterable[Json] = {
    val Xor.Right(documentNode) = document.as[JsonNode]
    val Xor.Right(schemaNode) = schema.as[JsonNode]

    JsonSchemaFactory
      .byDefault()
      .getValidator
      .validate(schemaNode, documentNode)
      .asScala
      .map { message =>
        val jacksonJson = message.asJson
        val circeJson = jacksonJson.asJson
        circeJson
      }
  }

}
