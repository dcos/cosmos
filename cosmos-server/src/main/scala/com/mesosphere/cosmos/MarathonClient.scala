package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.circe.JsonObject
import com.mesosphere.cosmos.circe.Decoders
import com.mesosphere.cosmos.error.GenericHttpError
import com.mesosphere.cosmos.error.MarathonAppNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.Deployment
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonAppResponse
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonAppsResponse
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Future
import io.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.handler.codec.http.HttpMethod

class MarathonClient(
  marathonUri: Uri,
  client: Service[Request, Response]
) extends ServiceClient(marathonUri) {

  def createApp(appJson: JsonObject)(implicit session: RequestSession): Future[Response] = {
    client(post("v2" / "apps" , Json.fromJsonObject(appJson)))
  }

  def modifyApp(
    appId: AppId,
    force: Boolean
  )(
    f: JsonObject => JsonObject
  )(
    implicit session: RequestSession
  ): Future[Response] = {
    client(get("v2" / "apps" / appId.toUri)).flatMap { response =>

      val appJson = Decoders.parse(response.contentString).asObject.get
        .apply("app").get.asObject.get
          // Note, Marathon appends extraneous fields when you fetch the current configuration.
          // Those are removed here to ensure the Marathon update succeeds.
          // Presently, those fields are:
          // - uris
          // - version
          .remove("uris")
          .remove("version")

      val uriPrefix = "v2" / "apps" / appId.toUri
      val uri = if (force) uriPrefix ? ("force" -> "true") else uriPrefix
      client(put(uri, Json.fromJsonObject(f(appJson))))
    }
  }

  def update(
    appId: AppId,
    appJson: JsonObject
  )(
    implicit session: RequestSession
  ): Future[Response] = {
    client(
      put(
        "v2" / "apps" / appId.toUri ? ("force" -> "true"),
        Json.fromJsonObject(appJson)
      )
    )
  }

  def getAppOption(
    appId: AppId
  )(
    implicit session: RequestSession
  ): Future[Option[MarathonAppResponse]] = {
    getAppResponse(appId).map {
      _ match {
        case Some(response) => Some(decodeJsonTo[MarathonAppResponse](response))
        case None => None
      }
    }
  }

  def getAppResponse(appId: AppId)(implicit session: RequestSession): Future[Option[Response]] = {
    val uri = "v2" / "apps" / appId.toUri
    client(get(uri)).map { response =>
      response.status match {
        case Status.Ok => Some(response)
        case Status.NotFound => None
        case s: Status =>
          throw GenericHttpError(HttpMethod.GET, uri, HttpResponseStatus.valueOf(s.code)).exception
      }
    }
  }

  def getAppRawJson(appId: AppId)(implicit session: RequestSession): Future[JsonObject] = {
    getAppResponse(appId).map {
      _ match {
        // No need to return an option as Some json should always be present.
        case Some(response) => decodeJsonTo[JsonObject](response)
        case None => throw MarathonAppNotFound(appId).exception // Should not happen
      }
    }
  }

  def getApp(appId: AppId)(implicit session: RequestSession): Future[MarathonAppResponse] = {
    getAppOption(appId).map { appOption =>
      appOption.getOrElse(throw MarathonAppNotFound(appId).exception)
    }
  }

  def listApps()(implicit session: RequestSession): Future[MarathonAppsResponse] = {
    val uri = "v2" / "apps"
    client(get(uri)).flatMap(decodeTo[MarathonAppsResponse](HttpMethod.GET, uri, _))
  }

  def deleteApp(
    appId: AppId,
    force: Boolean = false
  )(
    implicit session: RequestSession
  ): Future[Response] = {
    val uriPath = "v2" / "apps" / appId.toUri

    force match {
      case true => client(delete(uriPath ? ("force" -> "true")))
      case false => client(delete(uriPath))
    }
  }

  def listDeployments()(implicit session: RequestSession): Future[List[Deployment]] = {
    val uri = "v2" / "deployments"
    client(get(uri)).flatMap(decodeTo[List[Deployment]](HttpMethod.GET, uri, _))
  }
}
