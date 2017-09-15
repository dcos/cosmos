package com.mesosphere.cosmos.http

import com.mesosphere.cosmos.httpInterface
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Method
import com.twitter.finagle.http.Request
import com.twitter.io.Buf
import io.circe.Encoder
import io.circe.syntax._
import io.finch.Input

final case class HttpRequest(
  path: RpcPath,
  headers: Map[String, String],
  method: HttpRequestMethod
)

object HttpRequest {

  lazy val httpHostHeader = httpInterface().map(x => s"${x.getHostName}:${x.getPort}")

  def collectHeaders(entries: (String, Option[String])*): Map[String, String] = {
    entries
      .flatMap { case (key, value) => value.map(key -> _) }
      .toMap
  }

  def get(path: RpcPath, accept: MediaType): HttpRequest = {
    get(path, toHeader(accept))
  }

  def get(path: RpcPath, accept: Option[String]): HttpRequest = {
    HttpRequest(path, collectHeaders(Fields.Accept -> accept, Fields.Host -> httpHostHeader), Get())
  }

  def post[A](
    path: RpcPath,
    body: A,
    contentType: MediaType,
    accept: MediaType
  )(implicit encoder: Encoder[A]): HttpRequest = {
    post(path, body.asJson.noSpaces, toHeader(contentType), toHeader(accept))
  }

  def post(
    path: RpcPath,
    body: String,
    contentType: Option[String],
    accept: Option[String]
  ): HttpRequest = {
    val headers = collectHeaders(
      Fields.Accept -> accept,
      Fields.ContentType -> contentType,
      Fields.Host -> httpHostHeader
    )
    HttpRequest(path, headers, Post(Buf.Utf8(body)))
  }

  def post(
    path: RpcPath,
    body: Buf,
    contentType: MediaType,
    accept: MediaType
  ): HttpRequest = {
    val headers = collectHeaders(
      Fields.Accept -> toHeader(accept),
      Fields.ContentType -> toHeader(contentType),
      Fields.Host -> httpHostHeader
    )
    HttpRequest(path, headers, Post(body))
  }

  def toFinagle(cosmosRequest: HttpRequest): Request = {
    val finagleRequest = cosmosRequest.method match {
      case Get(params @ _*) =>
        Request(cosmosRequest.path.path, params: _*)
      case Post(buf) =>
        val req = Request(Method.Post, cosmosRequest.path.path)
        req.content = buf
        req.contentLength = buf.length.toLong
        req
    }

    finagleRequest.headerMap ++= cosmosRequest.headers
    finagleRequest
  }

  def toFinchInput(cosmosRequest: HttpRequest): Input = {
    val finagleRequest = toFinagle(cosmosRequest)

    Input(
      finagleRequest,
      finagleRequest.path.split("/")
    )
  }

  def toHeader(mediaType: MediaType): Option[String] = Some(mediaType.show)

}
