package com.mesosphere.cosmos

import cats.data.Xor.{Left, Right}
import com.mesosphere.cosmos.model.AppId
import com.mesosphere.cosmos.model.mesos.master._
import com.mesosphere.cosmos.circe.Decoders._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.io.Buf
import com.twitter.util.Future
import io.circe.Json
import io.circe.parse._

class AdminRouter(adminRouterUri: Uri, client: Service[Request, Response]) {

  private val baseUri = {
    val uri = adminRouterUri.toString
    if (uri.endsWith("/")) {
      uri.substring(0, uri.length)
    }
    else {
      uri
    }
  }

  private[this] def get(uri: Uri): Request = {
    RequestBuilder()
      .url(s"$baseUri${uri.toString}")
      .setHeader("Accept", "application/json;charset=utf-8")
      .buildGet
  }

  private[this] def post(uri: Uri, jsonBody: Json): Request = {
    RequestBuilder()
      .url(s"$baseUri${uri.toString}")
      .setHeader("Accept", "application/json;charset=utf-8")
      .setHeader("Content-Type", "application/json;charset=utf-8")
      .buildPost(Buf.Utf8(jsonBody.noSpaces))
  }

  private[this] def postForm(uri: Uri, postBody: String): Request = {
    RequestBuilder()
      .url(s"$baseUri${uri.toString}")
      .setHeader("Accept", "application/json; charset=utf-8")
      .setHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
      .buildPost(Buf.Utf8(postBody))
  }

  private[this] def delete(uri: Uri): Request = {
    RequestBuilder()
      .url(s"$baseUri${uri.toString}")
      .setHeader("Accept", "application/json;charset=utf-8")
      .buildDelete()
  }

  private[this] def validateResponseStatus(uri: Uri, response: Response): Future[Response] = {
    response.status match {
      case Status.Ok =>
        Future.value(response)
      case s: Status =>
        throw new GenericHttpError(uri, s)
    }
  }

  private[this] def decodeJsonTo[A](response: Response)(implicit d: io.circe.Decoder[A]): A = {
    response.headerMap.get("Content-Type") match {
      case Some(ct) if ct.startsWith("application/json") =>
        decode[A](response.contentString) match {
          case Left(err) => throw CirceError(err)
          case Right(a) => a
        }
      case a: Option[String] =>
        throw UnsupportedContentType(a, "application/json")
    }
  }

  private[this] def decodeTo[A](uri: Uri, response: Response)(implicit d: io.circe.Decoder[A]): Future[A] = {
    validateResponseStatus(uri, response)
      .map(decodeJsonTo[A])
  }

  def createApp(appJson: Json): Future[Response] = {
    client(post("marathon" / "v2" / "apps" , appJson))
  }

  def getAppOption(appId: AppId): Future[Option[MarathonAppResponse]] = {
    val uri = "marathon" / "v2" / "apps" / appId.toUri
    client(get(uri)).map { response =>
      response.status match {
        case Status.Ok => Some(decodeJsonTo[MarathonAppResponse](response))
        case Status.NotFound => None
        case s: Status => throw GenericHttpError(uri, s)
      }
    }
  }

  def getApp(appId: AppId): Future[MarathonAppResponse] = {
    getAppOption(appId).map { appOption =>
      appOption.getOrElse(throw MarathonAppNotFound(appId))
    }
  }

  def listApps(): Future[MarathonAppsResponse] = {
    val uri = "marathon" / "v2" / "apps"
    client(get(uri)).flatMap(decodeTo[MarathonAppsResponse](uri, _))
  }

  def deleteApp(appId: AppId, force: Boolean = false): Future[Response] = {
    val uriPath = "marathon" / "v2" / "apps" / appId.toUri

    force match {
      case true => client(delete(uriPath ? ("force" -> "true")))
      case false => client(delete(uriPath))
    }
  }

  def tearDownFramework(frameworkId: String): Future[MesosFrameworkTearDownResponse] = {
    val formData = Uri.empty.addParam("frameworkId", frameworkId)
    // scala-uri makes it convenient to encode the actual framework id, but it will think its for a Uri
    // so we strip the leading '?' that signifies the start of a query string
    val encodedString = formData.toString.substring(1)
    val uri = "mesos" / "master" / "teardown"
    client(postForm(uri, encodedString))
      .map(validateResponseStatus(uri, _))
      .flatMap { _ => Future.value(MesosFrameworkTearDownResponse()) }
  }

  def getMasterState(frameworkName: String): Future[MasterState] = {
    val uri = "mesos" / "master" / "state.json"
    client(get(uri)).flatMap(decodeTo[MasterState](uri, _))
  }
}
