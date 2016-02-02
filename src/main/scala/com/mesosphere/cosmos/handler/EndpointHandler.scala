package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.FinchExtensions._
import com.mesosphere.cosmos.http.{MediaTypes, MediaType}
import com.twitter.util.Future
import io.circe.{Encoder, Printer}
import io.finch._

import scala.reflect.ClassTag

private[cosmos] abstract class EndpointHandler[Request, Response]
(implicit
  decoder: DecodeRequest[Request],
  requestClassTag: ClassTag[Request],
  encoder: Encoder[Response],
  responseClassTag: ClassTag[Response]
)
  extends Function[Request, Future[Response]] {
  def accepts: MediaType
  def produces: MediaType

  // This field HAS to be lazy, otherwise a NullPointerException will be thrown on class initialization
  // because the description provided by `beTheExpectedType` is eagerly evaluated.
  lazy val reader: RequestReader[Request] = for {
    accept <- header("Accept").as[MediaType].should(beTheExpectedType(produces))
    contentType <- header("Content-Type").as[MediaType].should(beTheExpectedType(accepts))
    req <- body.as[Request]
  } yield req

  private val printer: Printer = Printer.noSpaces.copy(dropNullKeys = true, preserveOrder = true)
  lazy val encodeResponseType: EncodeResponse[Response] =
    EncodeResponse.fromString[Response](produces.show, produces.parameters.flatMap(_.get("charset"))) { response =>
      printer.pretty(encoder(response))
    }

}

object EndpointHandler {

  /**
    * Create an endpoint that will always return `resp` regardless of what request is sent to it.  Really only useful
    * for bootstrapping tests.
    */
  def const[Request, Response](resp: Response)(implicit
    decoder: DecodeRequest[Request],
    requestClassTag: ClassTag[Request],
    encoder: Encoder[Response],
    responseClassTag: ClassTag[Response]
  ): EndpointHandler[Request, Response] = {
    new EndpointHandler[Request, Response] {
      val accepts = MediaTypes.applicationJson
      val produces = MediaTypes.applicationJson
      override def apply(v1: Request): Future[Response] = Future.value(resp)
    }
  }
}
