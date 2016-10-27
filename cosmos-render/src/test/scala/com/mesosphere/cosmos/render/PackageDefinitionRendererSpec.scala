package com.mesosphere.cosmos.render

import cats.data.Xor
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.circe.Decoders.decodeAppId
import com.mesosphere.universe.v3.model._
import com.netaporter.uri.dsl._
import io.circe.{Json, JsonObject, ParsingFailure}
import io.circe.jawn._
import io.circe.syntax._
import org.scalatest.FreeSpec
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.io.Source
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

    "is Map[String, String] is left in tact" in {
      val json = Json.obj(
        "labels" -> Json.obj(
          "a" -> "A".asJson,
          "b" -> "B".asJson
        )
      )
      val pkg = packageDefinition(json.noSpaces)

      val Xor.Right(rendered) = PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pkg, None, None)

      val Xor.Right(labels) = rendered.cursor.get[Map[String, String]]("labels")
      assertResult("A")(labels("a"))
      assertResult("B")(labels("b"))
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
          packagingVersion = V3PackagingVersion,
          name = packageName,
          version = PackageDefinition.Version("1.2.3"),
          maintainer = "Mesosphere",
          description = "Testing user options",
          releaseVersion = PackageDefinition.ReleaseVersion(0).get,
          marathon = Some(Marathon(mustacheBytes)),
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

    "should correctly follow the priority [" +
      "config defaults -> " +
      "user options -> " +
      "resources -> " +
      "required labels -> " +
      "template rendered labels -> " +
      "non overridable labels -> " +
      "user specified appId" +
      "]" in {
      val s = classpathJsonString("/com/mesosphere/cosmos/render/test-schema.json")
      val Xor.Right(schema) = parse(s).map(_.asObject.get)

      val mustache =
        """
          |{
          |  "id": "{{opt.id}}",
          |  "uri": "{{resource.assets.uris.blob}}",
          |  "labels": {
          |    "DCOS_PACKAGE_NAME": "{{opt.name}}",
          |    "DCOS_PACKAGE_COMMAND": "{{opt.cmd}}"
          |  }
          |}
        """.stripMargin
      val mustacheBytes = ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))

      val pkg = V2Package(
        name = "test",
        version = PackageDefinition.Version("1.2.3"),
        releaseVersion = PackageDefinition.ReleaseVersion(0).get(),
        maintainer = "maintainer",
        description = "description",
        marathon = Marathon(mustacheBytes),
        config = Some(schema),
        resource = Some(V2Resource(
          assets = Some(Assets(
            uris = Some(Map(
              "blob" -> "http://someplace/blob"
            )),
            container = None
          ))
        )),
        command = Some(Command(List("something-not-overridden-cmd")))
      )

      val options = Json.obj(
        "opt" -> Json.obj(
          "id" -> "should-be-overridden-id".asJson,
          "name" -> "testing-name".asJson,
          "cmd" -> "should-be-overridden-cmd".asJson
        ),
        "resource" -> Json.obj(
          "assets" -> Json.obj(
            "uris" -> Json.obj(
              "blob" -> "should-be-overridden-blob".asJson
            )
          )
        )
      ).asObject.get

      val appId = AppId("/override")
      val Xor.Right(rendered) = PackageDefinitionRenderer.renderMarathonV2App(
        "http://someplace",
        pkg,
        Some(options),
        Some(appId)
      )

      val Xor.Right(actualAppId) = rendered.cursor.get[AppId]("id")
      assertResult(appId)(actualAppId)

      val Xor.Right(actualUri) = rendered.cursor.get[String]("uri")
      assertResult("http://someplace/blob")(actualUri)

      val Xor.Right(actualDcosPackageName) = rendered.hcursor.downField("labels").get[String]("DCOS_PACKAGE_NAME")
      assertResult("testing-name")(actualDcosPackageName)

      val Xor.Right(actualDcosPackageCommand) = rendered.hcursor.downField("labels").get[String]("DCOS_PACKAGE_COMMAND")
      assert(actualDcosPackageCommand !== "should-be-overridden-cmd")
    }

  }

  "renderMarathonV2App should" - {
    "result in error if no marathon template defined" in {
      val pkg = V3Package(
        name = "test",
        version = PackageDefinition.Version("1.2.3"),
        releaseVersion = PackageDefinition.ReleaseVersion(0).get(),
        maintainer = "maintainer",
        description = "description"
      )

      val Xor.Left(err) = PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pkg, None, None)
      assertResult(MissingMarathonV2AppTemplate)(err)
    }
    
    "result in error if options provided but no config defined" in {
      val mustache = """{"id": "{{option.id}}"}"""
      val mustacheBytes = ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))
      val pkg = V3Package(
        name = "test",
        version = PackageDefinition.Version("1.2.3"),
        releaseVersion = PackageDefinition.ReleaseVersion(0).get(),
        maintainer = "maintainer",
        description = "description",
        marathon = Some(Marathon(mustacheBytes))
      )
      val options = Json.obj(
        "option" -> Json.obj(
          "id" -> "should-be-overridden".asJson
        )
      ).asObject.get

      val Xor.Left(err) = PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pkg, Some(options), None)
      assertResult(OptionsNotAllowed)(err)
    }

    "result in error if rendered template is not valid json" in {
      val mustache = """{"id": "broken""""
      val mustacheBytes = ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))
      val pkg = V3Package(
        name = "test",
        version = PackageDefinition.Version("1.2.3"),
        releaseVersion = PackageDefinition.ReleaseVersion(0).get(),
        maintainer = "maintainer",
        description = "description",
        marathon = Some(Marathon(mustacheBytes))
      )

      val Xor.Left(RenderedTemplateNotJson(ParsingFailure(_, cause))) = PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pkg, None, None)
      assert(cause.isInstanceOf[jawn.IncompleteParseException])
    }

    "result in error if rendered template is valid json but is not valid json object" in {
      val mustache = """["not-an-object"]"""
      val mustacheBytes = ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))
      val pkg = V3Package(
        name = "test",
        version = PackageDefinition.Version("1.2.3"),
        releaseVersion = PackageDefinition.ReleaseVersion(0).get(),
        maintainer = "maintainer",
        description = "description",
        marathon = Some(Marathon(mustacheBytes))
      )

      val Xor.Left(err) = PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pkg, None, None)
      // when the rendered template is valid json, but not a valid json object the transformation
      // results in a None, not providing us any specific error, so we expect the cause to be 'null'
      // I realize this isn't great in scala, but we're dealing with exceptions and the java baggage
      // that come with them.
      assertResult(RenderedTemplateNotJson(null))(err)
    }

    "enforce appId is set to argument passed to argument if Some" in {
      val mustache = """{"id": "{{option.id}}"}"""
      val mustacheBytes = ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))
      val pkg = V2Package(
        name = "test",
        version = PackageDefinition.Version("1.2.3"),
        releaseVersion = PackageDefinition.ReleaseVersion(0).get(),
        maintainer = "maintainer",
        description = "description",
        marathon = Marathon(mustacheBytes),
        config = Some(buildConfig(Json.obj(
          "option" -> Json.obj(
            "id" -> "default".asJson
          )
        )))
      )

      val options = Json.obj(
        "option" -> Json.obj(
          "id" -> "should-be-overridden".asJson
        )
      ).asObject.get

      val appId = AppId("/override")
      val Xor.Right(rendered) = PackageDefinitionRenderer.renderMarathonV2App(
        "http://someplace",
        pkg,
        Some(options),
        Some(appId)
      )

      val Xor.Right(actualAppId) = rendered.cursor.get[AppId]("id")
      assertResult(appId)(actualAppId)
    }

    "property add resource object to options" - {
      "V2Package" in {
        val mustache =
          """{
            |  "some": "{{resource.assets.uris.blob}}"
            |}""".stripMargin
        val mustacheBytes = ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))
        val pkg = V2Package(
          name = "test",
          version = PackageDefinition.Version("1.2.3"),
          releaseVersion = PackageDefinition.ReleaseVersion(0).get(),
          maintainer = "maintainer",
          description = "description",
          marathon = Marathon(mustacheBytes),
          resource = Some(V2Resource(
            assets = Some(Assets(
              uris = Some(Map(
                "blob" -> "http://someplace/blob"
              )),
              container = None
            ))
          ))
        )
        val Xor.Right(rendered) = PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pkg, None, None)

        val Xor.Right(renderedValue) = rendered.cursor.get[String]("some")
        assertResult("http://someplace/blob")(renderedValue)
      }

      "V3Package" in {
        val mustache =
          """{
            |  "some": "{{resource.assets.uris.blob}}"
            |}""".stripMargin
        val mustacheBytes = ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))
        val pkg = V3Package(
          name = "test",
          version = PackageDefinition.Version("1.2.3"),
          releaseVersion = PackageDefinition.ReleaseVersion(0).get(),
          maintainer = "maintainer",
          description = "description",
          marathon = Some(Marathon(mustacheBytes)),
          resource = Some(V3Resource(
            assets = Some(Assets(
              uris = Some(Map(
                "blob" -> "http://someplace/blob"
              )),
              container = None
            ))
          ))
        )
        val Xor.Right(rendered) = PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pkg, None, None)

        val Xor.Right(renderedValue) = rendered.cursor.get[String]("some")
        assertResult("http://someplace/blob")(renderedValue)
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
      packagingVersion = V3PackagingVersion,
      name = "testing",
      version = PackageDefinition.Version("a.b.c"),
      maintainer = "foo@bar.baz",
      description = "blah",
      releaseVersion = PackageDefinition.ReleaseVersion(0).get,
      marathon = Some(Marathon(ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))))
    )
  }

  private[this] def classpathJsonString(resourceName: String): String = {
    val is = this.getClass.getResourceAsStream(resourceName)
    if (is == null) {
      throw new IllegalStateException(s"Unable to load classpath resource: $resourceName")
    }
    Source.fromInputStream(is).mkString
  }
}
