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

final case class TestContext(direct: Boolean)

object TestContext {
  def fromSystemProperties(): TestContext = {
    val property = "com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient.direct"

    TestContext(
      // TODO: replace getOrElse with get
      Option(System.getProperty(property)).map(_.toBoolean).getOrElse(false)
    )
  }
}

sealed trait HttpRequestBody
case object NoBody extends HttpRequestBody
final case class Monolithic(data: Buf) extends HttpRequestBody

sealed trait RpcPath {
  def path: String
}

final case class ServiceRpcPath(
  action: String
)(
  implicit testContext: TestContext
) extends RpcPath {
  override def path: String = {
    if (testContext.direct) s"/service/$action" else s"/cosmos/service/$action"
  }
}

final case class PackageRpcPath(
  action: String
)(
  implicit testContext: TestContext
) extends RpcPath {
  override def path: String = {
    // The path is always /package/...
    // TODO: Fix this...
    if (testContext.direct) s"/package/$action" else s"/package/$action"
  }
}

final case class RawRpcPath(path: String) extends RpcPath

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
