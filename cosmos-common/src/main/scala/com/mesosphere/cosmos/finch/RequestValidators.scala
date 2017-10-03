package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.finch.FinchExtensions._
import com.mesosphere.cosmos.http.Authorization
import com.mesosphere.cosmos.http.OriginHostScheme
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.http.CompoundMediaType
import com.mesosphere.http.MediaType
import com.mesosphere.util._
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Fields
import io.finch._
import shapeless.::
import shapeless.HNil

object RequestValidators {

  def noBody[Res](implicit
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[EndpointContext[Unit, Res]] = {
    val allValidators = baseValidator(produces) ::
      header(Fields.Host) ::
      header(urlSchemeHeader) ::
      headerOption(forwardedProtoHeader)

    allValidators.map { case authorization :: responseEncoder ::
      httpHost :: urlScheme :: forwardedProtocol :: HNil =>
      EndpointContext(
        (),
        RequestSession(
          authorization,
          OriginHostScheme(httpHost, forwardedProtocol.getOrElse(urlScheme))
        ),
        responseEncoder
      )
    }
  }

  def standard[Req, Res](implicit
    accepts: MediaTypedRequestDecoder[Req],
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[EndpointContext[Req, Res]] = {
    val contentTypeRule = beTheExpectedTypes(accepts.mediaTypedDecoder.mediaTypes.toList)
    val contentTypeValidator = header(Fields.ContentType).as[MediaType].should(contentTypeRule)

    val bodyValidator = body[Req, Application.Json](accepts.decoder, accepts.classTag)

    val allValidators = baseValidator(produces) ::
      header(Fields.Host) ::
      header(urlSchemeHeader) ::
      headerOption(forwardedProtoHeader) ::
      contentTypeValidator ::
      bodyValidator

    allValidators.map {
      case authorization :: responseEncoder :: httpHost :: urlScheme :: forwardedProtocol ::
        contentType :: requestBody :: HNil =>
        val session = RequestSession(
          authorization,
          OriginHostScheme(httpHost, forwardedProtocol.getOrElse(urlScheme)),
          Some(contentType)
        )
        EndpointContext(requestBody, session, responseEncoder)
    }
  }

  def baseValidator[Res](
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[Option[Authorization] :: MediaTypedEncoder[Res] :: HNil] = {
    val accept = header(Fields.Accept)
      .as[CompoundMediaType]
      .map { accept =>
        produces(accept) match {
          case Some(x) => x
          case None =>
            throw incompatibleAcceptHeader(produces.mediaTypes, accept.mediaTypes)
        }
      }
    val auth = headerOption(Fields.Authorization).map(_.map(Authorization))
    auth :: accept
  }

  val proxyValidator: Endpoint[(Uri, RequestSession)] = {
    val validators = param("url").map(Uri.parse) ::
      headerOption(Fields.Authorization).map(_.map(Authorization)) ::
      header(Fields.Host) ::
      header(urlSchemeHeader) ::
      headerOption(forwardedProtoHeader)

    validators.map {
      case queryParam :: authorization :: httpHost :: urlScheme :: forwardedProtocol :: HNil =>
        (
          queryParam,
          RequestSession(
            authorization,
            OriginHostScheme(httpHost, forwardedProtocol.getOrElse(urlScheme)),
            None
          )
        )
    }
  }
}
