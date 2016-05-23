package com.mesosphere.cosmos.handler

import cats.syntax.option._
import com.mesosphere.cosmos.circe.{DispatchingMediaTypedEncoder, MediaTypedDecoder, MediaTypedEncoder}
import com.mesosphere.cosmos.http.FinchExtensions._
import com.mesosphere.cosmos.http.{Authorization, MediaType, RequestSession}
import io.finch._

object RequestReaders {

  def noBody[Res](implicit
    produces: DispatchingMediaTypedEncoder[Res]
  ): RequestReader[EndpointContext[Unit, Res]] = {
    baseReader(produces).map { case (session, responseEncoder) =>
      EndpointContext((), session, responseEncoder)
    }
  }

  def standard[Req, Res](implicit
    accepts: MediaTypedDecoder[Req],
    produces: DispatchingMediaTypedEncoder[Res]
  ): RequestReader[EndpointContext[Req, Res]] = {
    for {
      (reqSession, responseEncoder) <- baseReader(produces)
      _ <- header("Content-Type").as[MediaType].should(beTheExpectedType(accepts.mediaType))
      req <- body.as[Req](accepts.decoder, accepts.classTag)
    } yield {
      EndpointContext(req, reqSession, responseEncoder)
    }
  }

  private[this] def baseReader[Res](
    produces: DispatchingMediaTypedEncoder[Res]
  ): RequestReader[(RequestSession, MediaTypedEncoder[Res])] = {
    for {
      responseEncoder <- header("Accept")
        .as[MediaType]
        .convert { accept =>
          produces(accept)
            .toRightXor(s"should match one of: ${produces.mediaTypes.map(_.show).mkString(", ")}")
        }
      auth <- headerOption("Authorization")
    } yield {
      (RequestSession(auth.map(Authorization(_))), responseEncoder)
    }
  }

}
