package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.handler._
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.model.thirdparty.marathon.MarathonApp
import com.mesosphere.universe.{PackageDetailsVersion, PackagingVersion, PackageFiles, PackageDetails}
import com.netaporter.uri.Uri
import com.twitter.finagle.http.RequestBuilder
import com.twitter.io.Buf
import com.twitter.util.{Future, Await}
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import io.finch.Input

final class UserOptionsSpec extends UnitSpec {

  "Merging JSON objects" - {

    "should pass on all examples" in {
      forAll (Examples) { (defaultsJson, optionsJson, mergedJson) =>
        assertResult(mergedJson) {
          PackageInstallHandler.merge(defaultsJson, optionsJson)
        }
      }
    }

    "should happen as part of package install" in {
      forAll (Examples) { (defaultsJson, optionsJson, mergedJson) =>
        val packageName = "options-test"
        val reqBody = InstallRequest(packageName, None, Some(optionsJson))
        val mustacheTemplate = buildMustacheTemplate(mergedJson)

        val packageFiles = PackageFiles(
          revision = "0",
          sourceUri = Uri.parse("in/memory/source"),
          packageJson = PackageDetails(
            packagingVersion = PackagingVersion("2.0"),
            name = packageName,
            version = PackageDetailsVersion("1.2.3"),
            maintainer = "Mesosphere",
            description = "Testing user options"
          ),
          marathonJsonMustache = mustacheTemplate,
          configJson = Some(buildConfig(Json.fromJsonObject(defaultsJson)))
        )
        val packages = Map(packageName -> packageFiles)
        val packageCache = MemoryPackageCache(packages)
        val packageRunner = new RecordingPackageRunner

        // these two imports provide the implicit DecodeRequest instances needed to instantiate Cosmos
        import com.mesosphere.cosmos.circe.Decoders._
        import com.mesosphere.cosmos.circe.Encoders._
        import io.finch.circe._
        val cosmos = new Cosmos(
          EndpointHandler.const(UninstallResponse(Nil)),
          new PackageInstallHandler(packageCache, packageRunner),
          new PackageRenderHandler(packageCache),
          EndpointHandler.const(SearchResponse(List.empty)),
          new PackageImportHandler,
          EndpointHandler.const(
            DescribeResponse(packageFiles.packageJson, packageFiles.marathonJsonMustache)
          ),
          EndpointHandler.const(ListVersionsResponse(Map.empty)),
          EndpointHandler.const(ListResponse(Nil)),
          EndpointHandler.const(PackageRepositoryListResponse(Nil)),
          EndpointHandler.const(PackageRepositoryAddResponse(Nil)),
          EndpointHandler.const(PackageRepositoryDeleteResponse(Nil)),
          CapabilitiesHandler()
        )
        val request = RequestBuilder()
          .url("http://dummy.cosmos.host/package/install")
          .addHeader("Content-Type", MediaTypes.InstallRequest.show)
          .addHeader("Accept", MediaTypes.InstallResponse.show)
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

}

private final class RecordingPackageRunner extends PackageRunner {

  private[cosmos] var marathonJson: Option[Json] = None

  override def launch(renderedConfig: Json): Future[MarathonApp] = {
    marathonJson = Some(renderedConfig)
    val Xor.Right(id) = renderedConfig.cursor.get[AppId]("id")
    Future.value(MarathonApp(id, Map.empty, List.empty, 0.0, 0.0, 1, None, None))
  }

}

private case class Config(
  `type`: String,
  default: Option[Json] = None,
  properties: Option[Map[String, Config]] = None
)
