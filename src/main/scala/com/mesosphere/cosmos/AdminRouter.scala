package com.mesosphere.cosmos

import cats.data.Xor.{Left, Right}
import com.mesosphere.cosmos.model.mesos.master._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.io.Buf
import com.twitter.util.Future
import io.circe.Json
import io.circe.parse._
import io.circe.generic.auto._

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

  private[this] def validateResponseStatus(uri: Uri, response: Response): CosmosResult[Response] = {
    response.status match {
      case Status.Ok =>
        Right(response)
      case s: Status => leftErrorNel(GenericHttpError(uri, s))
    }
  }

  private[this] def decodeJsonTo[A](response: Response)(implicit d: io.circe.Decoder[A]): CosmosResult[A] = {
    response.headerMap.get("Content-Type") match {
      case Some(ct) if ct.startsWith("application/json") =>
        decode[A](response.contentString) match {
          case Left(err) => leftErrorNel(CirceError(err))
          case Right(a) => Right(a)
        }
      case a: Option[String] =>
        leftErrorNel(UnsupportedContentType(a, "application/json"))
    }
  }

  private[this] def decodeTo[A](uri: Uri, response: Response)(implicit d: io.circe.Decoder[A]): CosmosResult[A] = {
    validateResponseStatus(uri, response)
      .flatMap(decodeJsonTo[A])
  }

  def createApp(appJson: Json): Future[Response] = {
    client(post("marathon" / "v2" / "apps" , appJson))
  }

  def getApp(appId: Uri): Future[CosmosResult[MarathonAppResponse]] = {
    val uri = "marathon" / "v2" / "apps" / appId
    client(get(uri)).map { response =>
      response.status match {
        case Status.Ok => decodeJsonTo(response)
        case Status.NotFound => leftErrorNel(MarathonAppNotFound(appId.toString))
        case s: Status => leftErrorNel(GenericHttpError(uri, s))
      }
    }
  }

  def listApps(): Future[CosmosResult[MarathonAppsResponse]] = {
    val uri = "marathon" / "v2" / "apps"
    client(get(uri)).map(decodeTo[MarathonAppsResponse](uri, _))
  }

  def deleteApp(appId: Uri, force: Boolean = false): Future[Response] = {
    force match {
      case true => client(delete("marathon" / "v2" / "apps" / appId ? ("force" -> "true")))
      case false => client(delete("marathon" / "v2" / "apps" / appId))
    }
  }

  def tearDownFramework(frameworkId: String): Future[CosmosResult[MesosFrameworkTearDownResponse]] = {
    val formData = Uri.empty.addParam("frameworkId", frameworkId)
    // scala-uri makes it convenient to encode the actual framework id, but it will think its for a Uri
    // so we strip the leading '?' that signifies the start of a query string
    val encodedString = formData.toString.substring(1)
    val uri = "mesos" / "master" / "teardown"
    client(postForm(uri, encodedString))
      .map(validateResponseStatus(uri, _))
      .flatMapXor { _ : Response => Future.value(Right(MesosFrameworkTearDownResponse())) }
  }

  def getMasterState(frameworkName: String): Future[CosmosResult[MasterState]] = {
    val uri = "mesos" / "master" / "state.json"
    client(get(uri)).map(decodeTo[MasterState](uri, _))
  }
}
