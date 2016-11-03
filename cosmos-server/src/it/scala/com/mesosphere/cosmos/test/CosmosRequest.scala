package com.mesosphere.cosmos.test

import com.mesosphere.cosmos.http.MediaType
import com.twitter.finagle.http.Method
import com.twitter.io.Buf
import io.circe.Encoder
import io.circe.syntax._

case class CosmosRequest(
  method: Method,
  path: String,
  body: Option[Buf],
  contentType: Option[String],
  accept: Option[String]
)

object CosmosRequest {

  def get(path: String, accept: MediaType): CosmosRequest = {
    CosmosRequest(Method.Get, path, body = None, contentType = None, accept = toHeader(accept))
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
    CosmosRequest(Method.Post, path, Some(Buf.Utf8(body)), contentType, accept)
  }

  private[this] def toHeader(mediaType: MediaType): Option[String] = Some(mediaType.show)

}
