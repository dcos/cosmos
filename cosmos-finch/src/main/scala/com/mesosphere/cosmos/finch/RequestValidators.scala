package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.finch.FinchExtensions._
import com.mesosphere.cosmos.http.{Authorization, CompoundMediaType, MediaType, RequestSession}
import com.twitter.util.Future
import io.finch._
import shapeless.{::, HNil}

object RequestValidators {

  def noBody[Res](implicit
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[EndpointContext[Unit, Res]] = {
    baseValidator(produces).map { case session :: responseEncoder :: HNil =>
      EndpointContext((), session, responseEncoder)
    }
  }

  def standard[Req, Res](implicit
    accepts: MediaTypedRequestDecoder[Req],
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[EndpointContext[Req, Res]] = {
    val r = baseValidator(produces)
    val h = header("Content-Type").as[MediaType].should(beTheExpectedType(accepts.mediaTypedDecoder.mediaType))
    val b = body.as[Req](accepts.decoder, accepts.classTag)
    val c = r :: h :: b
    c.map {
      case reqSession :: responseEncoder :: _ :: req :: HNil => EndpointContext(req, reqSession, responseEncoder)
    }
  }

  private[this] def baseValidator[Res](
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[RequestSession :: MediaTypedEncoder[Res] :: HNil] = {
    val accept = header("Accept")
            .as[CompoundMediaType]
            .mapAsync { accept =>
              produces(accept) match {
                case Some(x) =>
                  Future.value(x)
                case None =>
                  Future.exception(IncompatibleAcceptHeader(produces.mediaTypes, accept.mediaTypes))
              }
            }
    val auth = headerOption("Authorization").map { a => RequestSession(a.map(Authorization)) }
    auth :: accept
  }
}
