package com.mesosphere.cosmos.jsonschema

import cats.data.Xor
import io.circe.jawn.parse
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import org.scalatest.{FreeSpec, Tag}

import scala.io.Source

class JsonSchemaSpec extends FreeSpec {


  "JsonSchema should" - {
    "be able to validate a document against a schema" - {
      // the draft v4 json schema itself should be able to validate itself
      val jsonSchemaDraftV4String = classpathJsonString("/draftv4/schema")

      "as io.circe.JsonObject" in {
        val Xor.Right(parsedJson: Json) = parse(jsonSchemaDraftV4String)
        val xor = JsonSchema.jsonMatchesSchema(parsedJson, parsedJson)
        assert(xor.isRight)
      }

      "as io.circe.Json" in {
        val Xor.Right(parsedJson: Json) = parse(jsonSchemaDraftV4String)
        val jObject: JsonObject = parsedJson.asObject.get
        val xor = JsonSchema.jsonObjectMatchesSchema(jObject, jObject)
        assert(xor.isRight)
      }
    }

    "be able to extract default property values from a schema" - {
      val expected = JsonObject.fromMap(Map(
        "prop1" -> 57.asJson,
        "prop2" -> Json.obj(
          "sub1" -> "ta-da".asJson
        )
      ))

      "when schema does not use definition refs" in {
        val s = classpathJsonString("/com/mesosphere/cosmos/jsonschema/no-definition-ref-used.json")
        val Xor.Right(schema) = parse(s)
        val defaults = JsonSchema.extractDefaultsFromSchema(schema.asObject.get)
        assertResult(expected)(defaults)
      }

      "when schema does use definition refs" taggedAs Tag("https://mesosphere.atlassian.net/browse/DCOS-10455") ignore {
        val s = classpathJsonString("/com/mesosphere/cosmos/jsonschema/definition-ref-used.json")
        val Xor.Right(schema) = parse(s)
        val defaults = JsonSchema.extractDefaultsFromSchema(schema.asObject.get)
        assertResult(expected)(defaults)
      }

    }
  }

  private[this] def classpathJsonString(resourceName: String): String = {
    val is = this.getClass.getResourceAsStream(resourceName)
    if (is == null) {
      throw new IllegalStateException(s"Unable to load classpath resource: $resourceName")
    }
    Source.fromInputStream(is).mkString
  }
}
