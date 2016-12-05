package com.mesosphere.cosmos.http

import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Method
import com.twitter.finagle.http.Request
import com.twitter.io.Buf
import io.circe.Encoder
import io.circe.syntax._

case class HttpRequest(
  method: Method,
  path: String,
  headers: Map[String, String],
  body: HttpRequestBody
)

sealed trait HttpRequestBody
case object NoBody extends HttpRequestBody
case class Monolithic(data: Buf) extends HttpRequestBody

object HttpRequest {

  def collectHeaders(entries: (String, Option[String])*): Map[String, String] = {
    entries
      .flatMap { case (key, value) => value.map(key -> _) }
      .toMap
  }

  def get(path: String, accept: MediaType): HttpRequest = {
    get(path, toHeader(accept))
  }

  def get(path: String, accept: Option[String]): HttpRequest = {
    HttpRequest(Method.Get, path, collectHeaders(Fields.Accept -> accept), NoBody)
  }

  def post[A](
    path: String,
    body: A,
    contentType: MediaType,
    accept: MediaType
  )(implicit encoder: Encoder[A]): HttpRequest = {
    post(path, body.asJson.noSpaces, toHeader(contentType), toHeader(accept))
  }

  def post(
    path: String,
    body: String,
    contentType: Option[String],
    accept: Option[String]
  ): HttpRequest = {
    val headers = collectHeaders(Fields.Accept -> accept, Fields.ContentType -> contentType)
    HttpRequest(Method.Post, path, headers, Monolithic(Buf.Utf8(body)))
  }

  def post(
    path: String,
    body: Buf,
    contentType: MediaType,
    accept: MediaType
  ): HttpRequest = {
    val headers =
      collectHeaders(Fields.Accept -> toHeader(accept), Fields.ContentType -> toHeader(contentType))
    HttpRequest(Method.Post, path, headers, Monolithic(body))
  }

  def toFinagle(cosmosRequest: HttpRequest): Request = {
    val pathPrefix = if (cosmosRequest.path.startsWith("/")) "" else "/"
    val absolutePath = pathPrefix + cosmosRequest.path

    val finagleRequest = cosmosRequest.body match {
      case NoBody =>
        Request(absolutePath)
      case Monolithic(buf) =>
        val req = Request(Method.Post, absolutePath)
        req.content = buf
        req.contentLength = buf.length.toLong
        req
    }

    finagleRequest.headerMap ++= cosmosRequest.headers
    finagleRequest
  }

  def toHeader(mediaType: MediaType): Option[String] = Some(mediaType.show)

}
