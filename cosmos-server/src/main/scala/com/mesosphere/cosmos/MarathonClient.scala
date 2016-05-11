package com.mesosphere.cosmos

import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model.AppId
import com.mesosphere.cosmos.model.thirdparty.marathon.{MarathonAppResponse, MarathonAppsResponse}
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Future
import io.circe.Json
import org.jboss.netty.handler.codec.http.HttpMethod

class MarathonClient(
  marathonUri: Uri,
  client: Service[Request, Response]
) extends ServiceClient(marathonUri) {

  def createApp(appJson: Json)(implicit session: RequestSession): Future[Response] = {
    client(post("v2" / "apps" , appJson))
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
