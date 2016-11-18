package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.finch.FinchExtensions._
import com.mesosphere.cosmos.http.Authorization
import com.mesosphere.cosmos.http.CompoundMediaType
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.RequestSession
import com.twitter.finagle.http.Fields
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
    val contentTypeRule = beTheExpectedType(accepts.mediaTypedDecoder.mediaType)
    val contentTypeValidator = header(Fields.ContentType).as[MediaType].should(contentTypeRule)

    val bodyValidator = body.as[Req](accepts.decoder, accepts.classTag)

    val allValidators = baseValidator(produces) :: contentTypeValidator :: bodyValidator
    allValidators.map {
      case authorization :: responseEncoder :: contentType :: requestBody :: HNil =>
        val session = RequestSession(authorization, Some(contentType))
        EndpointContext(requestBody, session, responseEncoder)
    }
  }

  // TODO package-add: Unit tests in RequestValidatorsSpec
  def selectedBody[Req, Res](implicit
    accepts: DispatchingMediaTypedBodyParser[Req],
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[EndpointContext[Req, Res]] = {
    val contentTypeValidator = header(Fields.ContentType)
      .as[MediaType]
      .mapAsync { contentType =>
        accepts(contentType) match {
          case Some(bodyParser) => Future.value(contentType :: bodyParser :: HNil)
          case _ => Future.exception(IncompatibleContentTypeHeader(accepts.mediaTypes, contentType))
        }
      }

    val allValidators = baseValidator(produces) :: contentTypeValidator :: binaryBody
    allValidators.mapAsync {
      case authorization :: responseEncoder :: contentType :: bodyParser :: bodyBytes :: HNil =>
        Future.const(bodyParser(bodyBytes)).map { requestBody =>
          val session = RequestSession(authorization, Some(contentType))
          EndpointContext(requestBody, session, responseEncoder)
        }
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
