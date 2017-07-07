package com.mesosphere.cosmos.render

import cats.syntax.either._
import com.mesosphere.cosmos.bijection.CosmosConversions._
import com.mesosphere.cosmos.circe.Decoders.decode64
import com.mesosphere.cosmos.circe.Decoders.parse
import com.mesosphere.cosmos.circe.Decoders.parse64
import com.mesosphere.cosmos.error.CirceError
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.MarathonTemplateMustBeJsonObject
import com.mesosphere.cosmos.error.OptionsNotAllowed
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.model.StorageEnvelope
import com.mesosphere.cosmos.thirdparty.marathon.circe.Decoders.decodeAppId
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.model._
import com.netaporter.uri.dsl._
import com.twitter.bijection.Conversion.asMethod
import io.circe.Json
import io.circe.JsonObject
import io.circe.ParsingFailure
import io.circe.syntax._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import scala.io.Source

class PackageDefinitionRendererSpec extends FreeSpec with Matchers with TableDrivenPropertyChecks {

  "if .labels from .marathon.v2AppMustacheTemplate " - {
    "isn't Map[String, String] an error is returned" in {
      forAll(TestingPackages.packageDefinitions) { pkgDef =>
        val mustache =
          """
            |{
            |  "labels": {
            |    "idx": 0,
            |    "string": "value"
            |  }
            |}
          """.
            stripMargin

        val pd = packageDefinition(pkgDef)(mustache)

        val exception = intercept[CosmosException](PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pd, None, None))

        exception.error match {
          case CirceError(err) =>
            assertResult("String: El(DownField(idx),true,false),El(DownField(labels),true,false)")(err.getMessage)
          case _ =>
            fail("expected Circe Error")
        }
      }
    }

    "does not exist, a default empty object is used" in {
      forAll(TestingPackages.packageDefinitions) { pkgDef =>
        val mustache =
          """
            |{
            |  "env": {
            |    "some": "thing"
            |  }
            |}
          """.
            stripMargin

        val pd = packageDefinition(pkgDef)(mustache)

        val Right(some) = PackageDefinitionRenderer.renderMarathonV2App(
          "http://someplace",
          pd,
          None,
          None
        ).get.asJson.hcursor.downField("env").downField("some").as[String]

        assertResult("thing")(some)
      }
    }

    "is Map[String, String] is left in tact" in {
      forAll(TestingPackages.packageDefinitions) { pkgDef =>
        val json = Json.obj(
          "labels" -> Json.obj(
            "a" -> "A".asJson,
            "b" -> "B".asJson
          )
        )
        val pkg = packageDefinition(pkgDef)(json.noSpaces)

        val Some(rendered) = PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pkg, None, None)

        val Right(labels) = rendered.asJson.cursor.get[Map[String, String]]("labels")
        assertResult("A")(labels("a"))
        assertResult("B")(labels("b"))
      }
    }
  }

  "Merging JSON objects" - {

    "should happen as part of marathon AppDefinition rendering" in {
      forAll (Examples) { (defaultsJson, optionsJson, mergedJson) =>
        val packageName = "options-test"
        val mustacheTemplate = buildMustacheTemplate(mergedJson)
        val mustacheBytes = ByteBuffer.wrap(mustacheTemplate.getBytes(StandardCharsets.UTF_8))

        val packageDefinition = V3Package(
          packagingVersion = V3PackagingVersion,
          name = packageName,
          version = Version("1.2.3"),
          maintainer = "Mesosphere",
          description = "Testing user options",
          releaseVersion = ReleaseVersion(0),
          marathon = Some(Marathon(mustacheBytes)),
          config = Some(buildConfig(Json.fromJsonObject(defaultsJson)))
        )

        val Some(marathonJson) = PackageDefinitionRenderer.renderMarathonV2App(
          "http://someplace",
          packageDefinition,
          Some(optionsJson),
          None
        )

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
      val schema = parse(s).asObject.get

      val mustache =
        """
          |{
          |  "id": "{{opt.id}}",
          |  "uri": "{{resource.assets.uris.blob}}",
          |  "labels": {
          |    "DCOS_PACKAGE_NAME": "{{opt.name}}"
          |  }
          |}
        """.stripMargin
      val mustacheBytes = ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))

      val pkg = V2Package(
        name = "test",
        version = Version("1.2.3"),
        releaseVersion = ReleaseVersion(0),
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
          "name" -> "testing-name".asJson
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
      val renderedFocus = PackageDefinitionRenderer.renderMarathonV2App(
        "http://someplace",
        pkg,
        Some(options),
        Some(appId)
      ).get.asJson.hcursor

      renderedFocus.get[AppId]("id") shouldBe Right(appId)
      renderedFocus.get[String]("uri") shouldBe Right("http://someplace/blob")

      // Test that all of the labels are set correctly
      val labelFocus = renderedFocus.downField("labels")

      labelFocus.get[String](MarathonApp.nameLabel) shouldBe Right("test")
      labelFocus.get[String](MarathonApp.repositoryLabel) shouldBe Right("http://someplace")
      labelFocus.get[String](MarathonApp.versionLabel) shouldBe Right("1.2.3")
      labelFocus.get[String](MarathonApp.optionsLabel).map(
        parse64(_)
      ) shouldBe Right(options.asJson)
      labelFocus.get[String](MarathonApp.metadataLabel).map(
        decode64[label.v1.model.PackageMetadata](_)
      ) shouldBe Right(pkg.as[label.v1.model.PackageMetadata])
      labelFocus.get[String](MarathonApp.packageLabel).map(
        decode64[StorageEnvelope](_)
      ) shouldBe Right(StorageEnvelope(pkg))
    }

  }

  "renderMarathonV2App should" - {
    "result in error if no marathon template defined" in {
      val pkg = V3Package(
        name = "test",
        version = Version("1.2.3"),
        releaseVersion = ReleaseVersion(0),
        maintainer = "maintainer",
        description = "description"
      )

      val response = PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pkg, None, None)
      assertResult(None)(response)

    }

    "result in error if options provided but no config defined" in {
      val mustache = """{"id": "{{option.id}}"}"""
      val mustacheBytes = ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))
      val pkg = V3Package(
        name = "test",
        version = Version("1.2.3"),
        releaseVersion = ReleaseVersion(0),
        maintainer = "maintainer",
        description = "description",
        marathon = Some(Marathon(mustacheBytes))
      )
      val options = Json.obj(
        "option" -> Json.obj(
          "id" -> "should-be-overridden".asJson
        )
      ).asObject.get

      val exception = intercept[CosmosException](PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pkg, Some(options), None))
      assertResult(OptionsNotAllowed())(exception.error)
    }

    "result in error if rendered template is not valid json" in {
      val mustache = """{"id": "broken""""
      val mustacheBytes = ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))
      val pkg = V3Package(
        name = "test",
        version = Version("1.2.3"),
        releaseVersion = ReleaseVersion(0),
        maintainer = "maintainer",
        description = "description",
        marathon = Some(Marathon(mustacheBytes))
      )

      val exception = intercept[CosmosException](PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pkg, None, None))
      exception.error shouldBe a[CirceError]
      assertResult(exception.error.message)("exhausted input")
    }

    "result in error if rendered template is valid json but is not valid json object" in {
      val mustache = """["not-an-object"]"""
      val mustacheBytes = ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))
      val pkg = V3Package(
        name = "test",
        version = Version("1.2.3"),
        releaseVersion = ReleaseVersion(0),
        maintainer = "maintainer",
        description = "description",
        marathon = Some(Marathon(mustacheBytes))
      )

      val exception = intercept[CosmosException](PackageDefinitionRenderer.renderMarathonV2App("http://someplace", pkg, None, None))
      exception.error shouldBe MarathonTemplateMustBeJsonObject
    }

    "enforce appId is set to argument passed to argument if Some" in {
      val mustache = """{"id": "{{option.id}}"}"""
      val mustacheBytes = ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))
      val pkg = V2Package(
        name = "test",
        version = Version("1.2.3"),
        releaseVersion = ReleaseVersion(0),
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
      val rendered = PackageDefinitionRenderer.renderMarathonV2App(
        "http://someplace",
        pkg,
        Some(options),
        Some(appId)
      ).get.asJson

      val Right(actualAppId) = rendered.cursor.get[AppId]("id")
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
          version = Version("1.2.3"),
          releaseVersion = ReleaseVersion(0),
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
        val rendered = PackageDefinitionRenderer.renderMarathonV2App(
          "http://someplace",
          pkg,
          None,
          None
        ).get.asJson

        val Right(renderedValue) = rendered.cursor.get[String]("some")
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
          version = Version("1.2.3"),
          releaseVersion = ReleaseVersion(0),
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
        val rendered = PackageDefinitionRenderer.renderMarathonV2App(
          "http://someplace",
          pkg,
          None,
          None
        ).get.asJson

        val Right(renderedValue) = rendered.cursor.get[String]("some")
        assertResult("http://someplace/blob")(renderedValue)
      }

      "V4Package" in {
        val mustache =
          """{
            |  "some": "{{resource.assets.uris.blob}}"
            |}""".stripMargin
        val mustacheBytes = ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8))
        val pkg = universe.v4.model.V4Package(
          name = "test",
          version = Version("1.2.3"),
          releaseVersion = ReleaseVersion(0),
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
          )),
          upgradesFrom = Some(List()),
          downgradesTo = Some(List())
        )
        val rendered = PackageDefinitionRenderer.renderMarathonV2App(
          "http://someplace",
          pkg,
          None,
          None
        ).get.asJson

        val Right(renderedValue) = rendered.cursor.get[String]("some")
        assertResult("http://someplace/blob")(renderedValue)
      }
    }
  }

  "renderTemplate" - {
    "should not use html encoding for special characters" in {
      /* This means that we don't support rendering arrays or objects!
       * E.g.
       * {
       *   "array": {{arrayExample}},
       *   "object": {{objectExample}}
       * }
       */
      val template = """
      |{
      |  "string": "{{stringExample}}",
      |  "int": {{intExample}},
      |  "double": {{doubleExample}},
      |  "boolean": {{booleanExample}}
      |}
      |""".stripMargin

      val context = JsonObject.fromMap(
        Map(
          ("stringExample", "\n\'\"\\\r\t\b\f".asJson),
          ("intExample", 42.asJson),
          ("doubleExample", 42.1.asJson),
          ("booleanExample", Json.False)
        )
      )

      PackageDefinitionRenderer.renderTemplate(
        template,
        context
      ) shouldBe JsonObject.fromMap(
          Map(
            ("string", "\n\'\"\\\r\t\b\f".asJson),
            ("int", 42.asJson),
            ("double", 42.1.asJson),
            ("boolean", Json.False)
          )
      )
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


  private[this] def packageDefinition(pkgDef: universe.v4.model.PackageDefinition)(mustache: String) = {
    val marathon = Marathon(ByteBuffer.wrap(mustache.getBytes(StandardCharsets.UTF_8)))
    pkgDef match {
      case v2: universe.v3.model.V2Package =>
        v2.copy(marathon = marathon)
      case v3: universe.v3.model.V3Package =>
        v3.copy(marathon = Some(marathon))
      case v4: universe.v4.model.V4Package =>
        v4.copy(marathon = Some(marathon))
    }
  }

  private[this] def classpathJsonString(resourceName: String): String = {
    Option(this.getClass.getResourceAsStream(resourceName)) match {
      case Some(is) => Source.fromInputStream(is).mkString
      case _ => throw new IllegalStateException(s"Unable to load classpath resource: $resourceName")
    }
  }

}
