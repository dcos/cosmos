package com.mesosphere.cosmos.jsonschema

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.github.fge.jsonschema.main.JsonSchemaFactory
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.JsonObject
import io.circe.jawn.parse
import io.circe.syntax._
import scala.collection.JavaConverters._
import scala.util.Left
import scala.util.Right

object JsonSchema {

  import Jackson._

  type ValidationErrors = Iterable[Json]

  /**
    * Validates `document` against `schema` returning all validation failures.
    * @param document   The document to validation
    * @param schema     The schema to validate against
    * @param jsf        The configured factory used to acquire the validator used for the validation.
    * @return           Returns an [[scala.util.Either Either]] representing the result of validating
    *                   `document` against `schema`. [[scala.util.Left Left[ValidationErrors] ]]
    *                   will be returned containing all validation failures if they occur.
    *                   [[scala.util.Right Right[Unit] ]] Will be returned if no validation
    *                   failures occur.
    */
  def jsonObjectMatchesSchema(
    document: JsonObject,
    schema: JsonObject
  )(
    implicit jsf: JsonSchemaFactory
  ): Either[ValidationErrors, Unit] = {
    jsonMatchesSchema(Json.fromJsonObject(document), Json.fromJsonObject(schema))
  }

  /**
    * Validates `document` against `schema` returning all validation failures.
    * @param document   The document to validation
    * @param schema     The schema to validate against
    * @param jsf        The configured factory used to acquire the validator used for the validation.
    * @return           Returns an [[scala.util.Either Either]] representing the result of
    *                   validating `document` against `schema`.
    *                   [[scala.util.Left Left[ValidationErrors] ]] will be returned containing
    *                   all validation failures if they occur. [[scala.util.Right Right[Unit] ]]
    *                   will be returned if no validation failures occur.
    */
  def jsonMatchesSchema(
    document: Json,
    schema: Json
  )(
    implicit jsf: JsonSchemaFactory
  ): Either[Iterable[Json], Unit] = {
    val Right(documentNode) = document.as[JsonNode]
    val Right(schemaNode) = schema.as[JsonNode]

    val validationErrors = jsf.getValidator.validate(schemaNode, documentNode)
    if (validationErrors.isSuccess) {
      Right[ValidationErrors, Unit](())
    } else {
      Left(
        validationErrors
          .asScala
          .map { message =>
            val jacksonJson = message.asJson
            val circeJson = jacksonJson.asJson
            circeJson
          }
      )
    }
  }

  /**
    * Traverse a json schema `schema` and create a document representing all the default property
    * values defined in the schema. If the schema has nested properties, the nesting will be
    * preserved.
    * @param schema The schema to extract default property values from
    * @return       A document representing each property with its corresponding default value
    */
  def extractDefaultsFromSchema(schema: JsonObject): JsonObject = {
    val props = schema("properties").getOrElse(Json.Null)

    filterDefaults(props)
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

  /**
    * The validation library we use to validate the schema operates on the Jackson AST for the json document.
    * This object has a set of utilities to convert between circe and jackson.
    */
  private[this] object Jackson {

    implicit val JsonNodeEncoder: Encoder[JsonNode] = Encoder.instance(jsonNodeToCirceJson)

    implicit val JsonNodeDecoder: Decoder[JsonNode] = Decoder.instance { hcursor =>
      Right(circeJsonToJsonNode(hcursor.top.get))
    }

    private[this] def jsonNodeToCirceJson(node: JsonNode): Json = {
      // Inefficient, but good enough for now
      val Right(json) = parse(node.toString)
      json
    }

    private[this] def circeJsonToJsonNode(json: Json): JsonNode = {
      json.fold(
        jsonNull = JsonNodeFactory.instance.nullNode,
        jsonBoolean = JsonNodeFactory.instance.booleanNode,
        jsonNumber = { jsonNumber =>
          (jsonNumber.toBigInt, jsonNumber.toBigDecimal) match {
            case (Some(bigInt), _) => JsonNodeFactory.instance.numberNode(bigInt.underlying())
            case (_, Some(bigDec)) => JsonNodeFactory.instance.numberNode(bigDec.underlying())
            case _ => throw new NumberFormatException
          }
        },
        jsonString = JsonNodeFactory.instance.textNode,
        jsonArray = { arr =>
          val arrayNode = JsonNodeFactory.instance.arrayNode()
          arrayNode.addAll(arr.map(circeJsonToJsonNode).asJava)
        },
        jsonObject = { obj =>
          val objectNode = JsonNodeFactory.instance.objectNode()
          objectNode.setAll(obj.toMap.mapValues(circeJsonToJsonNode).asJava)
        }
      )
    }

  }

}
