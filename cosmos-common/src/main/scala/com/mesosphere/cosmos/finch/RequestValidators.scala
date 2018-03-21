package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.finch.FinchExtensions._
import com.mesosphere.cosmos.http.Authorization
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.http.CompoundMediaType
import com.mesosphere.http.MediaType
import com.mesosphere.http.OriginHostScheme
import com.mesosphere.util.ForwardedProtoHeader
import com.mesosphere.util.UrlSchemeHeader
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
      header[String](Fields.Host) ::
      header[String](UrlSchemeHeader) ::
      headerOption[String](ForwardedProtoHeader)

    allValidators.map { case authorization :: responseEncoder ::
      httpHost :: urlScheme :: forwardedProtocol :: HNil =>
      EndpointContext(
        (),
        RequestSession(
          authorization,
          OriginHostScheme(
            httpHost,
            parseScheme(forwardedProtocol.getOrElse(urlScheme))
          )
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
    val contentTypeValidator = header[MediaType](Fields.ContentType).should(contentTypeRule)

    val bodyValidator = body[Req, Application.Json](accepts.decoder, accepts.classTag)

    val allValidators = baseValidator(produces) ::
      header[String](Fields.Host) ::
      header[String](UrlSchemeHeader) ::
      headerOption[String](ForwardedProtoHeader) ::
      contentTypeValidator ::
      bodyValidator

    allValidators.map {
      case authorization :: responseEncoder :: httpHost :: urlScheme :: forwardedProtocol ::
        contentType :: requestBody :: HNil =>
        val session = RequestSession(
          authorization,
          OriginHostScheme(
            httpHost,
            parseScheme(forwardedProtocol.getOrElse(urlScheme))
          ),
          Some(contentType)
        )
        EndpointContext(requestBody, session, responseEncoder)
    }
  }

  def baseValidator[Res](
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[Option[Authorization] :: MediaTypedEncoder[Res] :: HNil] = {
    val accept = header[CompoundMediaType](Fields.Accept)
      .map { accept =>
        produces(accept) match {
          case Some(x) => x
          case None =>
            throw incompatibleAcceptHeader(produces.mediaTypes, accept.mediaTypes)
        }
      }
    val auth = headerOption[String](Fields.Authorization).map(_.map(Authorization))
    auth :: accept
  }

  val proxyValidator: Endpoint[(Uri, RequestSession)] = {
    val validators = param[String]("url").map(Uri.parse) ::
      headerOption[String](Fields.Authorization).map(_.map(Authorization)) ::
      header[String](Fields.Host) ::
      header[String](UrlSchemeHeader) ::
      headerOption[String](ForwardedProtoHeader)

    validators.map {
      case queryParam :: authorization :: httpHost :: urlScheme ::
           forwardedProtocol :: HNil =>
        (
          queryParam,
          RequestSession(
            authorization,
            OriginHostScheme(
              httpHost,
              parseScheme(forwardedProtocol.getOrElse(urlScheme))
            ),
            None
          )
        )
    }
  }

  private def parseScheme(value: String): OriginHostScheme.Scheme = {
    OriginHostScheme.Scheme(value).getOrElse(throw incompatibleScheme(value))
  }
}
