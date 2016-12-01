package com.mesosphere.cosmos.http

import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Method
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Version.Http11
import com.twitter.io.Buf
import com.twitter.io.Reader
import io.circe.Encoder
import io.circe.syntax._
import java.io.InputStream

case class CosmosRequest(
  method: Method,
  path: String,
  headers: Map[String, String],
  body: CosmosRequestBody
)

sealed trait CosmosRequestBody
case object NoBody extends CosmosRequestBody
case class Monolithic(data: Buf) extends CosmosRequestBody
case class Chunked(data: Reader) extends CosmosRequestBody

object CosmosRequest {

  def collectHeaders(entries: (String, Option[String])*): Map[String, String] = {
    entries
      .flatMap { case (key, value) => value.map(key -> _) }
      .toMap
  }

  def get(path: String, accept: MediaType): CosmosRequest = {
    get(path, toHeader(accept))
  }

  def get(path: String, accept: Option[String]): CosmosRequest = {
    CosmosRequest(Method.Get, path, collectHeaders(Fields.Accept -> accept), NoBody)
  }

  def post[A](
    path: String,
    body: A,
    contentType: MediaType,
    accept: MediaType
  )(implicit encoder: Encoder[A]): CosmosRequest = {
    post(path, body.asJson.noSpaces, toHeader(contentType), toHeader(accept))
  }

  def post(
    path: String,
    body: String,
    contentType: Option[String],
    accept: Option[String]
  ): CosmosRequest = {
    val headers = collectHeaders(Fields.Accept -> accept, Fields.ContentType -> contentType)
    CosmosRequest(Method.Post, path, headers, Monolithic(Buf.Utf8(body)))
  }

  def post(
    path: String,
    body: Buf,
    contentType: MediaType,
    accept: MediaType
  ): CosmosRequest = {
    val headers =
      collectHeaders(Fields.Accept -> toHeader(accept), Fields.ContentType -> toHeader(contentType))
    CosmosRequest(Method.Post, path, headers, Monolithic(body))
  }

  def toFinagle(cosmosRequest: CosmosRequest): Request = {
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
      case Chunked(reader) =>
        Request(Http11, cosmosRequest.method, absolutePath, reader)
    }

    finagleRequest.headerMap ++= cosmosRequest.headers
    finagleRequest
  }

  def toHeader(mediaType: MediaType): Option[String] = Some(mediaType.show)

}
