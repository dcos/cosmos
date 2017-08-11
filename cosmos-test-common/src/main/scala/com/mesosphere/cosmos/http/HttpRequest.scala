package com.mesosphere.cosmos.http

import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Method
import com.twitter.finagle.http.Request
import com.twitter.io.Buf
import io.circe.Encoder
import io.circe.syntax._
import io.finch.Input

final case class HttpRequest(
  method: Method,
  path: RpcPath,
  headers: Map[String, String],
  body: HttpRequestBody
)

object HttpRequest {

  def collectHeaders(entries: (String, Option[String])*): Map[String, String] = {
    entries
      .flatMap { case (key, value) => value.map(key -> _) }
      .toMap
  }

  def get(path: RpcPath, accept: MediaType): HttpRequest = {
    get(path, toHeader(accept))
  }

  def get(path: RpcPath, accept: Option[String]): HttpRequest = {
    HttpRequest(Method.Get, path, collectHeaders(Fields.Accept -> accept), NoBody)
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
    val headers = collectHeaders(Fields.Accept -> accept, Fields.ContentType -> contentType)
    HttpRequest(Method.Post, path, headers, Monolithic(Buf.Utf8(body)))
  }

  def post(
    path: RpcPath,
    body: Buf,
    contentType: MediaType,
    accept: MediaType
  ): HttpRequest = {
    val headers = collectHeaders(
      Fields.Accept -> toHeader(accept),
      Fields.ContentType -> toHeader(contentType)
    )
    HttpRequest(Method.Post, path, headers, Monolithic(body))
  }

  def toFinagle(cosmosRequest: HttpRequest): Request = {
    val finagleRequest = cosmosRequest.body match {
      case NoBody =>
        Request(cosmosRequest.path.path)
      case Monolithic(buf) =>
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
