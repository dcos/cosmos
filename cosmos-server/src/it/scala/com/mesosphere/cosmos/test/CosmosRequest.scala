package com.mesosphere.cosmos.test

import com.mesosphere.cosmos.http.MediaType
import com.twitter.finagle.http.Method
import com.twitter.io.Buf
import com.twitter.io.Reader
import io.circe.Encoder
import io.circe.syntax._
import java.io.InputStream

case class CosmosRequest private (
  method: Method,
  path: String,
  accept: Option[String],
  contentType: Option[String],
  customHeaders: Map[String, String],
  body: CosmosRequestBody
)

sealed trait CosmosRequestBody
case object NoBody extends CosmosRequestBody
case class Monolithic(data: Buf) extends CosmosRequestBody
case class Chunked(data: Reader) extends CosmosRequestBody

object CosmosRequest {

  def get(path: String, accept: MediaType): CosmosRequest = {
    CosmosRequest(Method.Get, path, toHeader(accept), None, Map.empty, NoBody)
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
    CosmosRequest(Method.Post, path, accept, contentType, Map.empty, Monolithic(Buf.Utf8(body)))
  }

  def post(
    path: String,
    body: InputStream,
    contentType: MediaType,
    accept: MediaType,
    customHeaders: Map[String, String]
  ): CosmosRequest = {
    CosmosRequest(
      Method.Post,
      path,
      toHeader(accept),
      toHeader(contentType),
      customHeaders,
      Chunked(Reader.fromStream(body))
    )
  }

  private[this] def toHeader(mediaType: MediaType): Option[String] = Some(mediaType.show)

}
