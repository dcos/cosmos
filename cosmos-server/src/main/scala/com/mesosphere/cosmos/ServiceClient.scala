package com.mesosphere.cosmos

import cats.data.Xor.{Left, Right}
import com.mesosphere.cosmos.http.{MediaTypeOps, MediaTypes, RequestSession}
import com.netaporter.uri.Uri
import com.twitter.finagle.http.RequestConfig.Yes
import com.twitter.finagle.http.{Request, RequestBuilder, Response, Status}
import com.twitter.io.Buf
import com.twitter.util.Future
import io.circe.Json
import io.circe.parse._
import org.jboss.netty.handler.codec.http.HttpMethod

abstract class ServiceClient(baseUri: Uri) {

  private[this] val cleanedBaseUri: String = Uris.stripTrailingSlash(baseUri)
  private[this] val cosmosVersion: String = BuildProperties().cosmosVersion

  protected def get(uri: Uri)(implicit session: RequestSession): Request = {
    baseRequestBuilder(uri)
      .setHeader("Accept", MediaTypes.applicationJson.show)
      .buildGet
  }

  protected def post(uri: Uri, jsonBody: Json)(implicit session: RequestSession): Request = {
    baseRequestBuilder(uri)
      .setHeader("Accept", MediaTypes.applicationJson.show)
      .setHeader("Content-Type", MediaTypes.applicationJson.show)
      .buildPost(Buf.Utf8(jsonBody.noSpaces))
  }

  protected def postForm(uri: Uri, postBody: String)(implicit session: RequestSession): Request = {
    baseRequestBuilder(uri)
      .setHeader("Accept", MediaTypes.applicationJson.show)
      .setHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
      .buildPost(Buf.Utf8(postBody))
  }

  protected def delete(uri: Uri)(implicit session: RequestSession): Request = {
    baseRequestBuilder(uri)
      .setHeader("Accept", MediaTypes.applicationJson.show)
      .buildDelete()
  }

  protected def validateResponseStatus(method: HttpMethod, uri: Uri, response: Response): Future[Response] = {
    response.status match {
      case Status.Ok =>
        Future.value(response)
      case s: Status =>
        throw new GenericHttpError(method, uri, s)
    }
  }

  protected def decodeJsonTo[A](response: Response)(implicit d: io.circe.Decoder[A]): A = {
    response.headerMap.get("Content-Type") match {
      case Some(ct) =>
        http.MediaType.parse(ct).map { mediaType =>
          // Marathon and Mesos don't specify 'charset=utf-8' on it's json, so we are lax in our comparison here.
          MediaTypeOps.compatibleIgnoringParameters(MediaTypes.applicationJson, mediaType) match {
            case false =>
              throw UnsupportedContentType.forMediaType(List(MediaTypes.applicationJson), Some(mediaType))
            case true =>
              decode[A](response.contentString) match {
                case Left(err) => throw CirceError(err)
                case Right(a) => a
              }
          }
        }.get
      case _ =>
        throw UnsupportedContentType(List(MediaTypes.applicationJson))
    }
  }

  protected def decodeTo[A](method: HttpMethod, uri: Uri, response: Response)(implicit d: io.circe.Decoder[A]): Future[A] = {
    validateResponseStatus(method, uri, response)
      .map(decodeJsonTo[A])
  }

  private[cosmos] final def baseRequestBuilder(uri: Uri)(implicit session: RequestSession): RequestBuilder[Yes, Nothing] = {
    val builder = RequestBuilder()
      .url(s"$cleanedBaseUri${uri.toString}")
      .setHeader("User-Agent", s"cosmos/$cosmosVersion")

    session.authorization match {
      case Some(auth) => builder.setHeader("Authorization", auth.headerValue)
      case _ => builder
    }
  }
}
