package com.mesosphere.cosmos.test

import cats.data.Xor
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.http.{Authorization, MediaType, RequestSession}
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.RequestConfig.Yes
import com.twitter.finagle.http._
import com.twitter.io.Buf
import com.twitter.util.{Await, Future, Try}
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.scalatest.Matchers
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicInteger

object CosmosIntegrationTestClient extends Matchers {

  val adminRouter = {
    val property = dcosUri.name
    val ar = Try {
      Option(System.getProperty(property))
        .getOrElse(throw new AssertionError(s"Missing system property '$property'"))
    }
      .map { dh =>
        val dcosHost: String = Uris.stripTrailingSlash(dh)
        val ar: Uri = dcosHost
        val mar: Uri = dcosHost / "marathon"
        val mesos: Uri = dcosHost / "mesos"
        (ar, mar, mesos)
      }
      .flatMap { case (adminRouterUri, marathonUri, mesosMasterUri) =>
        Trys.join(
          Services.adminRouterClient(adminRouterUri).map { adminRouterUri -> _},
          Services.marathonClient(marathonUri).map { marathonUri -> _ },
          Services.mesosClient(mesosMasterUri).map { mesosMasterUri -> _ }
        )
      }
      .map { case (adminRouterClient, marathon, mesosMaster) =>
        new AdminRouter(
          new AdminRouterClient(adminRouterClient._1, adminRouterClient._2),
          new MarathonClient(marathon._1, marathon._2),
          new MesosMasterClient(mesosMaster._1, mesosMaster._2)
        )
      }

    ar.get
  }

  implicit val Session = RequestSession(
    sys.env.get("COSMOS_AUTHORIZATION_HEADER").map { token =>
      // Start as 7 to trim the leading 'token=', then take the first 10 characters
      val tokenDisplay = token.substring(7, 17)
      CosmosClient.logger.info(s"Loaded authorization token '$tokenDisplay...' from environment")
      Authorization(token)
    }
  )

  object CosmosClient {
    lazy val logger = LoggerFactory.getLogger("com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient")

    val property = "com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient.uri"
    private[this] val client = Try {
        Option(System.getProperty(property))
          .getOrElse(throw new AssertionError(s"Missing system property '$property "))
      }
      .flatMap { uri =>
        Services.httpClient("cosmosIntegrationTestClient", uri, RequestLogging)
      }
      .get

    def requestBuilder(endpointPath: String): RequestBuilder[Yes, Nothing] = Session match {
      case RequestSession(Some(auth)) =>
        RequestBuilder()
          .url(s"http://localhost:7070/$endpointPath")
          .setHeader("Authorization", auth.headerValue)
      case _ =>
        RequestBuilder()
          .url(s"http://localhost:7070/$endpointPath")
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
      client(req)
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

    private[cosmos] object RequestLogging extends SimpleFilter[Request, Response] {
      val counter = new AtomicInteger()
      override def apply(req: Request, service: Service[Request, Response]): Future[Response] = {
        val c = counter.getAndIncrement
        logger.debug(s"$c -> ${req.method} ${req.path} ${fmtHeaders(req.headerMap)} ${req.contentString}")
        service(req) map { res =>
          logger.debug(s"$c <- ${res.status.code} ${res.status.reason} ${fmtHeaders(res.headerMap)} ${res.contentString}")
          res
        }
      }

      private[this] def fmtHeaders(h: HeaderMap): String = {
        h.map {
          case ("Authorization", v) => s"Authorization: ****"
          case (k, v) => s"$k: $v"
        } mkString " "
      }
    }
  }
}
