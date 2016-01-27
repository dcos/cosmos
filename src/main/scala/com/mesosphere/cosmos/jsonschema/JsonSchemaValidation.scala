package com.mesosphere.cosmos.jsonschema

import cats.data.Xor
import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.mesosphere.cosmos.jsonschema.Jackson._
import io.circe.{JsonObject, Json}

private[cosmos] object JsonSchemaValidation {

  private[cosmos] def matchesSchema(document: JsonObject, schema: JsonObject): Boolean = {
    matchesSchema(Json.fromJsonObject(document), Json.fromJsonObject(schema))
  }
  private[cosmos] def matchesSchema(document: Json, schema: Json): Boolean = {
    val Xor.Right(documentNode) = document.as[JsonNode]
    val Xor.Right(schemaNode) = schema.as[JsonNode]

    JsonSchemaFactory
      .byDefault()
      .getValidator
      .validate(schemaNode, documentNode)
      .isSuccess
  }

}
