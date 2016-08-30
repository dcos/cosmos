package com.mesosphere.cosmos.jsonschema

import cats.data.Xor
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.circe.jawn.parse
import io.circe.{Decoder, Encoder, Json}

import scala.collection.JavaConverters._

private[jsonschema] object Jackson {

  private[jsonschema] implicit val JsonNodeEncoder: Encoder[JsonNode] = Encoder.instance(jsonNodeToCirceJson)

  private[jsonschema] implicit val JsonNodeDecoder: Decoder[JsonNode] = Decoder.instance { hcursor =>
    Xor.Right(circeJsonToJsonNode(hcursor.top))
  }

  private[this] def jsonNodeToCirceJson(node: JsonNode): Json = {
    // Inefficient, but good enough for now
    val Xor.Right(json) = parse(node.toString)
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
