package com.mesosphere.cosmos.jsonschema

import com.github.fge.jsonschema.main.JsonSchemaFactory
import io.circe.Json
import io.circe.JsonObject
import io.circe.jawn.parse
import io.circe.syntax._
import org.scalatest.FreeSpec
import org.scalatest.Tag
import scala.io.Source
import scala.util.Right

class JsonSchemaSpec extends FreeSpec {

  private[this] implicit val jsf = JsonSchemaFactory.byDefault()

  "JsonSchema should" - {
    "be able to validate a document against a schema" - {
      // the draft v4 json schema itself should be able to validate itself
      val jsonSchemaDraftV4String = classpathJsonString("/draftv4/schema")

      "as io.circe.JsonObject" in {
        val Right(parsedJson: Json) = parse(jsonSchemaDraftV4String)
        val xor = JsonSchema.jsonMatchesSchema(parsedJson, parsedJson)
        assert(xor.isRight)
      }

      "as io.circe.Json" in {
        val Right(parsedJson: Json) = parse(jsonSchemaDraftV4String)
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
        val Right(schema) = parse(s)
        val defaults = JsonSchema.extractDefaultsFromSchema(schema.asObject.get)
        assertResult(expected)(defaults)
      }

      "when schema does use definition refs" taggedAs Tag("https://mesosphere.atlassian.net/browse/DCOS-10455") ignore {
        val s = classpathJsonString("/com/mesosphere/cosmos/jsonschema/definition-ref-used.json")
        val Right(schema) = parse(s)
        val defaults = JsonSchema.extractDefaultsFromSchema(schema.asObject.get)
        assertResult(expected)(defaults)
      }

    }
  }

  private[this] def classpathJsonString(resourceName: String): String = {
    Option(this.getClass.getResourceAsStream(resourceName)) match {
      case Some(is) => Source.fromInputStream(is).mkString
      case _ => throw new IllegalStateException(s"Unable to load classpath resource: $resourceName")
    }
  }

}
