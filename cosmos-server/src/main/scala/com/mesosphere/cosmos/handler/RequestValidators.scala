package com.mesosphere.cosmos.handler

import cats.syntax.option._
import com.mesosphere.cosmos.circe.{DispatchingMediaTypedEncoder, MediaTypedDecoder, MediaTypedEncoder}
import com.mesosphere.cosmos.http.FinchExtensions._
import com.mesosphere.cosmos.http.{Authorization, MediaType, RequestSession}
import io.finch._
import shapeless.{::, HNil}

object RequestValidators {

  def noBody[Res](implicit
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[EndpointContext[Unit, Res]] = {
    baseValidator(produces).map { case (session, responseEncoder) =>
      EndpointContext((), session, responseEncoder)
    }
  }

  def standard[Req, Res](implicit
    accepts: MediaTypedDecoder[Req],
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[EndpointContext[Req, Res]] = {
    val r = baseValidator(produces)
    val h = header("Content-Type").as[MediaType].should(beTheExpectedType(accepts.mediaType))
    val b = body.as[Req](accepts.decoder, accepts.classTag)
    val c = r :: h :: b
    c.map {
      case (reqSession, responseEncoder) :: _ :: req :: HNil => EndpointContext(req, reqSession, responseEncoder)
    }
  }

  private[this] def baseValidator[Res](
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[(RequestSession, MediaTypedEncoder[Res])] = {
    val h = header("Accept")
            .as[MediaType]
            .convert { accept =>
              produces(accept)
                .toRightXor(s"should match one of: ${produces.mediaTypes.map(_.show).mkString(", ")}")
            }
    val a = headerOption("Authorization")
    val c = h :: a 
    c.map {
      case responseEncoder :: auth :: HNil => (RequestSession(auth.map(Authorization)), responseEncoder)
    }
  }
}
