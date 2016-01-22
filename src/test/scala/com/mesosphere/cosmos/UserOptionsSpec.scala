package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.model._
import com.twitter.finagle.http.RequestBuilder
import com.twitter.io.Buf
import com.twitter.util.{Future, Await}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import io.finch.{Output, Input}

final class UserOptionsSpec extends UnitSpec {

  "Merging JSON objects" - {

    "should pass on all examples" in {
      forAll (Examples) { (defaultsJson, optionsJson, mergedJson) =>
        assertResult(mergedJson) {
          PackageInstall.merge(defaultsJson, optionsJson)
        }
      }
    }

    "should happen as part of package install" in {
      forAll (Examples) { (defaultsJson, optionsJson, mergedJson) =>
        val packageName = "options-test"
        val reqBody = InstallRequest(packageName, None, Some(optionsJson))
        val mustacheTemplate = buildMustacheTemplate(mergedJson)

        val packageFiles = PackageFiles(
          version = "1.2.3",
          revision = "0",
          commandJson = Json.obj(),
          configJson = buildConfig(Json.fromJsonObject(defaultsJson)).asJson,
          marathonJsonMustache = mustacheTemplate,
          packageJson = PackageDefinition(
            name = packageName,
            version = "1.2.3",
            maintainer = "Mesosphere",
            description = "Testing user options"
          ),
          resourceJson = Resource()
        )
        val packages = Map(packageName -> packageFiles)
        val packageCache = MemoryPackageCache(packages)
        val packageRunner = new RecordingPackageRunner

        val cosmos = new Cosmos(
          packageCache,
          packageRunner,
          (r : UninstallRequest) => { Future.value(UninstallResponse(Nil)) }
        )
        val request = RequestBuilder()
          .url("http://dummy.cosmos.host/v1/package/install")
          .buildPost(Buf.Utf8(reqBody.asJson.noSpaces))

        val Some((_, eval)) = cosmos.packageInstall(Input(request))
        val _ = Await.result(eval.value)

        val marathonJson = packageRunner.marathonJson
          .flatMap(_.asObject)
          .getOrElse(JsonObject.empty)
        val expectedOptions = keyValify(mergedJson)
        val hasAllOptions = expectedOptions.forall { case (k, v) =>
          marathonJson(k).map(_.toString).contains(v)
        }

        assert(hasAllOptions)
      }
    }

  }

  private[this] val JFalse = Json.bool(false)

  private[this] val Examples = Table(
    ("defaults JSON", "options JSON", "merged JSON"),
    (JsonObject.empty, JsonObject.empty, JsonObject.empty),

    (JsonObject.empty,
      JsonObject.singleton("a", JFalse),
      JsonObject.singleton("a", JFalse)),

    (JsonObject.singleton("a", JFalse),
      JsonObject.empty,
      JsonObject.singleton("a", JFalse)),

    (JsonObject.singleton("a", JFalse),
      JsonObject.singleton("a", JFalse),
      JsonObject.singleton("a", JFalse)),

    (JsonObject.singleton("a", Json.obj("a" -> JFalse)),
      JsonObject.singleton("a", Json.obj()),
      JsonObject.singleton("a", Json.obj("a" -> JFalse))),

    (JsonObject.singleton("a", Json.obj("a" -> JFalse)),
      JsonObject.singleton("a", Json.obj("a" -> JFalse)),
      JsonObject.singleton("a", Json.obj("a" -> JFalse))),

    (JsonObject.singleton("a", Json.obj("a" -> JFalse)),
      JsonObject.singleton("a", Json.obj("b" -> JFalse)),
      JsonObject.singleton("a", Json.obj("a" -> JFalse, "b" -> JFalse)))
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

  private[this] def buildConfig(defaultsJson: Json): Json = {
    defaultsJson.fold(
      jsonNull = Map("type" -> "null".asJson, "default" -> defaultsJson).asJson,
      jsonBoolean = boolean => Map("type" -> "boolean".asJson, "default" -> defaultsJson).asJson,
      jsonNumber = number => Map("type" -> "number".asJson, "default" -> defaultsJson).asJson,
      jsonString = string => Map("type" -> "string".asJson, "default" -> defaultsJson).asJson,
      jsonArray = array => Map("type" -> "array".asJson, "default" -> defaultsJson).asJson,
      jsonObject =  { obj =>
        Map(
          "type" -> "object".asJson,
          "properties" -> obj.toMap.mapValues(buildConfig).asJson
        ).asJson
      }
    )
  }

  private[this] def buildMustacheTemplate(mustacheScopeJson: JsonObject): String = {
    keyValify(mustacheScopeJson)
      .keysIterator
      .map(name => s""""$name":{{$name}}""")
      .mkString("{", ",", "}")
  }

}

private final class RecordingPackageRunner extends PackageRunner {

  private[cosmos] var marathonJson: Option[Json] = None

  override def launch(renderedConfig: Json): Future[Output[Json]] = {
    marathonJson = Some(renderedConfig)
    Future.value(Output.payload(Json.obj()))
  }

}

private case class Config(
  `type`: String,
  default: Option[Json] = None,
  properties: Option[Map[String, Config]] = None
)
