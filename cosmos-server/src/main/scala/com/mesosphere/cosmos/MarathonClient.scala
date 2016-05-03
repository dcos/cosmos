package com.mesosphere.cosmos

import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.model.AppId
import com.mesosphere.cosmos.model.thirdparty.marathon.{MarathonAppsResponse, MarathonAppResponse}
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Future
import io.circe.Json
import org.jboss.netty.handler.codec.http.HttpMethod

class MarathonClient(
  marathonUri: Uri,
  client: Service[Request, Response],
  authorization: Option[String]
) extends ServiceClient(marathonUri, authorization) {

  def createApp(appJson: Json): Future[Response] = {
    client(post("v2" / "apps" , appJson))
  }

  def getAppOption(appId: AppId): Future[Option[MarathonAppResponse]] = {
    val uri = "v2" / "apps" / appId.toUri
    client(get(uri)).map { response =>
      response.status match {
        case Status.Ok => Some(decodeJsonTo[MarathonAppResponse](response))
        case Status.NotFound => None
        case s: Status => throw GenericHttpError(HttpMethod.GET, uri, s)
      }
    }
  }

  def getApp(appId: AppId): Future[MarathonAppResponse] = {
    getAppOption(appId).map { appOption =>
      appOption.getOrElse(throw MarathonAppNotFound(appId))
    }
  }

  def listApps(): Future[MarathonAppsResponse] = {
    val uri = "v2" / "apps"
    client(get(uri)).flatMap(decodeTo[MarathonAppsResponse](HttpMethod.GET, uri, _))
  }

  def deleteApp(appId: AppId, force: Boolean = false): Future[Response] = {
    val uriPath = "v2" / "apps" / appId.toUri

    force match {
      case true => client(delete(uriPath ? ("force" -> "true")))
      case false => client(delete(uriPath))
    }
  }

}
