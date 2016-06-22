package com.mesosphere.cosmos

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import cats.data.Xor
import com.mesosphere.cosmos.handler._
import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.circe.Decoders._
import com.mesosphere.cosmos.thirdparty.marathon.model._
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.finagle.http.RequestBuilder
import com.twitter.io.Buf
import com.twitter.util.{Await, Future}
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
        val reqBody = rpc.v1.model.InstallRequest(packageName, None, Some(optionsJson))
        val mustacheTemplate = buildMustacheTemplate(mergedJson)
        val mustacheBytes = ByteBuffer.wrap(mustacheTemplate.getBytes(StandardCharsets.UTF_8))

        val packageDefinition = internal.model.PackageDefinition(
          packagingVersion = universe.v3.model.V3PackagingVersion.instance,
          name = packageName,
          version = universe.v3.model.PackageDefinition.Version("1.2.3"),
          maintainer = "Mesosphere",
          description = "Testing user options",
          releaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion(0),
          marathon = Some(universe.v3.model.Marathon(mustacheBytes)),
          config = Some(buildConfig(Json.fromJsonObject(defaultsJson)))
        )

        val packages = Map(packageName -> packageDefinition)
        val packageCache = MemoryPackageCache(packages, Uri.parse("in/memory/source"))
        val packageRunner = new RecordingPackageRunner

        val cosmos = new Cosmos(
          constHandler(rpc.v1.model.UninstallResponse(Nil)),
          new PackageInstallHandler(packageCache, packageRunner),
          new PackageRenderHandler(packageCache),
          constHandler(rpc.v1.model.SearchResponse(List.empty)),
          constHandler(packageDefinition),
          constHandler(rpc.v1.model.ListVersionsResponse(Map.empty)),
          constHandler(rpc.v1.model.ListResponse(Nil)),
          constHandler(rpc.v1.model.PackageRepositoryListResponse(Nil)),
          constHandler(rpc.v1.model.PackageRepositoryAddResponse(Nil)),
          constHandler(rpc.v1.model.PackageRepositoryDeleteResponse(Nil)),
          new CapabilitiesHandler
        )
        val request = RequestBuilder()
          .url("http://dummy.cosmos.host/package/install")
          .addHeader("Content-Type", MediaTypes.InstallRequest.show)
          .addHeader("Accept", MediaTypes.V1InstallResponse.show)
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

  private[this] def constHandler[Request, Response](
    resp: Response
  ): EndpointHandler[Request, Response] = {
    new EndpointHandler[Request, Response] {
      override def apply(v1: Request)(implicit session: RequestSession): Future[Response] = {
        Future.value(resp)
      }
    }
  }

}

private final class RecordingPackageRunner extends PackageRunner {

  private[cosmos] var marathonJson: Option[Json] = None

  override def launch(renderedConfig: Json)(implicit session: RequestSession): Future[MarathonApp] = {
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
