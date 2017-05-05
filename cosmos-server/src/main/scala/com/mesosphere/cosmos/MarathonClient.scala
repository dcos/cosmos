package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.circe.JsonObject
import _root_.io.circe.parser._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.circe.Decoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.{AppId, MarathonAppResponse, MarathonAppsResponse}
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.HttpMethod

class MarathonClient(
  marathonUri: Uri,
  client: Service[Request, Response]
) extends ServiceClient(marathonUri) {

  def createApp(appJson: JsonObject)(implicit session: RequestSession): Future[Response] = {
    client(post("v2" / "apps" , Json.fromJsonObject(appJson)))
  }

  def updateApp(appId: AppId, appJson: JsonObject)(implicit session: RequestSession): Future[Response] = {
    client(put("v2" / "apps" / appId.toUri, Json.fromJsonObject(appJson)))
  }

  def getRawApp(appId: AppId)(implicit session: RequestSession): Future[JsonObject] = {
    client(get("v2" / "apps" / appId.toUri)).map(response => response.contentString)
      .flatMap { content =>
        parse(content) match {
          case Left(failure) => throw failure
          case Right(json) =>
            val app = json.asObject.get.apply("app").get.asObject.get
            Future.value(app)
        }
      }
  }

  def getAppOption(appId: AppId)(implicit session: RequestSession): Future[Option[MarathonAppResponse]] = {
    val uri = "v2" / "apps" / appId.toUri
    client(get(uri)).map { response =>
      response.status match {
        case Status.Ok => Some(decodeJsonTo[MarathonAppResponse](response))
        case Status.NotFound => None
        case s: Status => throw GenericHttpError(HttpMethod.GET, uri, s)
      }
    }
  }

  def getApp(appId: AppId)(implicit session: RequestSession): Future[MarathonAppResponse] = {
    getAppOption(appId).map { appOption =>
      appOption.getOrElse(throw MarathonAppNotFound(appId))
    }
  }

  def setAppEnvvar(appId: AppId, envvar: String, value: String)(implicit session: RequestSession): Future[Response] = {
    val uri = "v2" / "apps" / appId.toUri
    client(get(uri)).map(response => response.contentString)
      .map { content =>
        parse(content) match {
          case Left(failure) => throw failure
          case Right(json) =>
            val app = json.asObject.get.apply("app").get.asObject.get
              .remove("uris").remove("version")
            app.add("env", Json.fromJsonObject(app.apply("env").get.asObject.get.add(envvar, Json.fromString(value))))
        }
      }
      .flatMap(jsonObj => client(put(uri, Json.fromJsonObject(jsonObj))))
  }

  def listApps()(implicit session: RequestSession): Future[MarathonAppsResponse] = {
    val uri = "v2" / "apps"
    client(get(uri)).flatMap(decodeTo[MarathonAppsResponse](HttpMethod.GET, uri, _))
  }

  def deleteApp(appId: AppId, force: Boolean = false)(implicit session: RequestSession): Future[Response] = {
    val uriPath = "v2" / "apps" / appId.toUri

    force match {
      case true => client(delete(uriPath ? ("force" -> "true")))
      case false => client(delete(uriPath))
    }
  }

}
