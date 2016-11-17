package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.finch.FinchExtensions._
import com.mesosphere.cosmos.http.Authorization
import com.mesosphere.cosmos.http.CompoundMediaType
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.RequestSession
import com.twitter.concurrent.AsyncStream
import com.twitter.io.Buf
import com.twitter.util.Future
import io.finch._
import shapeless.::
import shapeless.HNil

object RequestValidators {

  def noBody[Res](implicit
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[EndpointContext[Unit, Res]] = {
    baseValidator(produces).map { case authorization :: responseEncoder :: HNil =>
      EndpointContext((), RequestSession(authorization, contentType = None), responseEncoder)
    }
  }

  def standard[Req, Res](implicit
    accepts: MediaTypedRequestDecoder[Req],
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[EndpointContext[Req, Res]] = {
    val a = baseValidator(produces)
    val h = header("Content-Type").as[MediaType].should(beTheExpectedType(accepts.mediaTypedDecoder.mediaType))
    val b = body.as[Req](accepts.decoder, accepts.classTag)
    val c = a :: h :: b
    c.map { case authorization :: responseEncoder :: contentType :: req :: HNil =>
      EndpointContext(req, RequestSession(authorization, Some(contentType)), responseEncoder)
    }
  }

  // TODO package-add: Include standard request validation, e.g. Content-Type/Accept, etc.
  def streamed[Req, Res](toReq: (AsyncStream[Buf], Long) => Req)(implicit
    resEncoder: MediaTypedEncoder[Res]
  ): Endpoint[EndpointContext[Req, Res]] = {
    val sessionValidator = header("Content-Type").as[MediaType].map { contentType =>
      RequestSession(authorization = None, contentType = Some(contentType))
    }

    val validators = asyncBody :: header("X-Dcos-Content-Length") :: sessionValidator
    validators.map { case bufStream :: bodySize :: session :: HNil =>
      // TODO package-add: Better error handling for request data extraction (e.g. toLong)
      EndpointContext(toReq(bufStream, bodySize.toLong), session, resEncoder)
    }
  }

  private[this] def baseValidator[Res](
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[Option[Authorization] :: MediaTypedEncoder[Res] :: HNil] = {
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
    val auth = headerOption("Authorization").map(_.map(Authorization))
    auth :: accept
  }
}
