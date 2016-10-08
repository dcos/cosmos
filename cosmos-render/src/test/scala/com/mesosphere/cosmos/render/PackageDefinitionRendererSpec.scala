package com.mesosphere.cosmos.render

import cats.data.Xor
import com.mesosphere.universe
import com.mesosphere.universe.v3.model.V3Package
import com.netaporter.uri.dsl._
import io.circe.{Json, JsonObject}
import io.circe.syntax._
import org.scalatest.FreeSpec
import org.scalatest.prop.TableDrivenPropertyChecks

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class PackageDefinitionRendererSpec extends FreeSpec with TableDrivenPropertyChecks {

  "if .labels from .marathon.v2AppMustacheTemplate " - {
    "isn't Map[String, String] an error is returned" in {
      val mustache =
        """
          |{
          |  "labels": {
          |    "idx": 0,
          |    "string": "value"
          |  }
          |}
        """.stripMargin

      val pd = packageDefinition(mustache)

      PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pd, None, None) match {
        case Xor.Left(InvalidLabelSchema(err)) =>
          assertResult("String: El(DownField(idx),true,false),El(DownField(labels),true,false)")(err.getMessage)
        case _ =>
          fail("expected InvalidLabelSchemaError")
      }
    }

    "does not exist, a default empty object is used" in {
      val mustache =
        """
          |{
          |  "env": {
          |    "some": "thing"
          |  }
          |}
        """.stripMargin

      val pd = packageDefinition(mustache)

      val Xor.Right(some) = PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pd, None, None)
        .flatMap(_.hcursor.downField("env").downField("some").as[String])

      assertResult("thing")(some)
    }
  }

  "Merging JSON objects" - {

    "should pass on all examples" in {
      forAll (Examples) { (defaultsJson, optionsJson, mergedJson) =>
        assertResult(mergedJson)(PackageDefinitionRenderer.merge(defaultsJson, optionsJson))
      }
    }

    "should happen as part of package install" in {
      forAll (Examples) { (defaultsJson, optionsJson, mergedJson) =>
        val packageName = "options-test"
        val mustacheTemplate = buildMustacheTemplate(mergedJson)
        val mustacheBytes = ByteBuffer.wrap(mustacheTemplate.getBytes(StandardCharsets.UTF_8))

        val packageDefinition = V3Package(
          packagingVersion = universe.v3.model.V3PackagingVersion,
          name = packageName,
          version = universe.v3.model.PackageDefinition.Version("1.2.3"),
          maintainer = "Mesosphere",
          description = "Testing user options",
          releaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion(0).get,
          marathon = Some(universe.v3.model.Marathon(mustacheBytes)),
          config = Some(buildConfig(Json.fromJsonObject(defaultsJson)))
        )

        val Xor.Right(marathonJson) = PackageDefinitionRenderer.renderMarathonV2App(
          "http://someplace",
          packageDefinition,
          Some(optionsJson),
          None
        ).map(_.asObject.get)

        val expectedOptions = keyValify(mergedJson)
        val hasAllOptions = expectedOptions.forall { case (k, v) =>
          marathonJson(k).map(_.toString).contains(v)
        }

        assert(hasAllOptions)
      }
    }

  }

  private[this] val Examples = Table(
    ("defaults JSON", "options JSON", "merged JSON"),
    (JsonObject.empty, JsonObject.empty, JsonObject.empty),

    (JsonObject.empty,
      JsonObject.singleton("a", Json.False),
      JsonObject.singleton("a", Json.False)),

    (JsonObject.singleton("a", Json.False),
      JsonObject.empty,
      JsonObject.singleton("a", Json.False)),

    (JsonObject.singleton("a", Json.False),
      JsonObject.singleton("a", Json.False),
      JsonObject.singleton("a", Json.False)),

    (JsonObject.singleton("a", Json.obj("a" -> Json.False)),
      JsonObject.singleton("a", Json.obj()),
      JsonObject.singleton("a", Json.obj("a" -> Json.False))),

    (JsonObject.singleton("a", Json.obj("a" -> Json.False)),
      JsonObject.singleton("a", Json.obj("a" -> Json.False)),
      JsonObject.singleton("a", Json.obj("a" -> Json.False))),

    (JsonObject.singleton("a", Json.obj("a" -> Json.False)),
      JsonObject.singleton("a", Json.obj("b" -> Json.False)),
      JsonObject.singleton("a", Json.obj("a" -> Json.False, "b" -> Json.False)))
  )

  private[this] def keyValify(mustacheScopeJson: JsonObject): Map[String, String] = {
    keyValifyMap(mustacheScopeJson.toMap, Vector.empty).toMap
  }

  private[this] def keyValifyMap(
    jsonMap: Map[String, Json],
    path: Seq[String]
  ): TraversableOnce[(String, String)] = {
    jsonMap
      .flatMap { case (key, value) =>
        keyValifyJson(value, path :+ key)
      }
  }

  private[this] def keyValifyJson(
    json: Json,
    path: Seq[String]
  ): TraversableOnce[(String, String)] = {
    lazy val joinedPath = path.mkString(".")
    json.fold(
      jsonNull = Seq((joinedPath, "null")),
      jsonBoolean = boolean => Seq((joinedPath, boolean.toString)),
      jsonNumber = number => Seq((joinedPath, number.toString)),
      jsonString = string => Seq((joinedPath, string)),
      jsonArray = { elements =>
        val indexMap = elements
          .zipWithIndex
          .map { case (j, i) => (i.toString, j) }
          .toMap
        keyValifyMap(indexMap, path)
      },
      jsonObject = obj => keyValifyMap(obj.toMap, path)
    )
  }

  private[this] def buildConfig(defaultsJson: Json): JsonObject = {
    defaultsJson.fold(
      jsonNull = JsonObject.fromMap(Map("type" -> "null".asJson, "default" -> defaultsJson)),
      jsonBoolean = boolean => JsonObject.fromMap(Map("type" -> "boolean".asJson, "default" -> defaultsJson)),
      jsonNumber = number => JsonObject.fromMap(Map("type" -> "number".asJson, "default" -> defaultsJson)),
      jsonString = string => JsonObject.fromMap(Map("type" -> "string".asJson, "default" -> defaultsJson)),
      jsonArray = array => JsonObject.fromMap(Map("type" -> "array".asJson, "default" -> defaultsJson)),
      jsonObject =  { obj =>
        JsonObject.fromMap(Map(
          "type" -> "object".asJson,
          "properties" -> obj.toMap.mapValues(buildConfig).asJson
        ))
      }
    )
  }

  private[this] def buildMustacheTemplate(mustacheScopeJson: JsonObject): String = {
    val parameters = keyValify(mustacheScopeJson)
      .keysIterator
      .map(name => (name, s"{{$name}}"))
      .toMap

    (parameters + (("id", "\"options-test\"")))
      .map { case (name, value) => s""""$name":$value""" }
      .mkString("{", ",", "}")
  }


  private[this] def packageDefinition(mustache: String) = {
    V3Package(
      packagingVersion = universe.v3.model.V3PackagingVersion,
      name = "testing",
      version = universe.v3.model.PackageDefinition.Version("a.b.c"),
      maintainer = "foo@bar.baz",
      description = "blah",
      releaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion(0).get,
      marathon = Some(universe.v3.model.Marathon(ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))))
    )
  }

}
