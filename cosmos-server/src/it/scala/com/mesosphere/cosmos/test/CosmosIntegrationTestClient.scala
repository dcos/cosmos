package com.mesosphere.cosmos.test

import cats.data.Xor
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.http.MediaType
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.RequestConfig.Yes
import com.twitter.finagle.http._
import com.twitter.io.Buf
import com.twitter.util.{Await, Future, Try}
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.scalatest.Matchers
import org.slf4j.LoggerFactory

object CosmosIntegrationTestClient extends Matchers {

  val adminRouter = {
    val property = dcosUri.name
    val ar = Try {
      Option(System.getProperty(property))
        .getOrElse(throw new AssertionError(s"Missing system property '$property "))
    }
      .map { dh =>
        val dcosHost: String = Uris.stripTrailingSlash(dh)
        val mar: Uri = dcosHost / "marathon"
        val mesos: Uri = dcosHost / "mesos"
        mar -> mesos
      }
      .flatMap { case (marathon, mesosMaster) =>
        Trys.join(
          Services.marathonClient(marathon).map { marathon -> _ },
          Services.mesosClient(mesosMaster).map { mesosMaster -> _ }
        )
      }
      .map { case (marathon, mesosMaster) =>
        new AdminRouter(
          new MarathonClient(marathon._1, marathon._2, authorization = None),
          new MesosMasterClient(mesosMaster._1, mesosMaster._2, authorization = None)
        )
      }

    ar.get
  }

  object CosmosClient {
    lazy val logger = LoggerFactory.getLogger("com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient")

    val property = "com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient.uri"
    private[this] val client = Try {
      Option(System.getProperty(property))
        .getOrElse(throw new AssertionError(s"Missing system property '$property "))
    }
      .flatMap { uri =>
        Services.httpClient("cosmosIntegrationTestClient", uri)
      }
      .get

    def requestBuilder(endpointPath: String): RequestBuilder[Yes, Nothing] = {
      RequestBuilder().url(s"http://localhost:7070/$endpointPath")
    }

    def buildPost[Req](
      path: String,
      requestBody: Req,
      contentType: MediaType,
      accept: MediaType
    )(implicit encoder: Encoder[Req]): Request = {
      requestBuilder(path)
        .addHeader("Content-type", contentType.show)
        .addHeader("Accept", accept.show)
        .buildPost(Buf.Utf8(requestBody.asJson.noSpaces))
    }

    def apply(request: Request): Response = {
      Await.result(submit(request))
    }

    def submit(req: Request): Future[Response] = {
      logger.debug(s">>> ${req.method} ${req.path} ${fmtHeaders(req.headerMap)} ${req.contentString}")
      req.getContentString()
      client(req) map { res =>
        logger.debug(s"<<< ${res.status.code} ${res.status.reason} ${fmtHeaders(res.headerMap)} ${res.contentString}")
        res
      }
    }

    def callEndpoint[Req, Res](
      path: String,
      requestBody: Req,
      requestMediaType: MediaType,
      responseMediaType: MediaType,
      status: Status = Status.Ok
    )(implicit decoder: Decoder[Res], encoder: Encoder[Req]): Xor[ErrorResponse, Res] = {
      val request = buildPost(path, requestBody, requestMediaType, responseMediaType)
      val response = CosmosClient(request)
      assertResult(status)(response.status)

      if (response.status.code / 100 == 2) {
        decode[Res](response.contentString) match {
          case Xor.Left(_) => fail("Could not decode as successful response: " + response.contentString)
          case Xor.Right(successfulResponse) => Xor.Right(successfulResponse)
        }
      } else {
        decode[ErrorResponse](response.contentString) match {
          case Xor.Left(_) => fail("Could not decode as error response: " + response.contentString)
          case Xor.Right(errorResponse) => Xor.Left(errorResponse)
        }
      }
    }

    def fmtHeaders(h: HeaderMap): String = {
      h.map { case (k, v) => s"$k: $v" } mkString " "
    }
  }
}
