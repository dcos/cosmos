package com.mesosphere.cosmos.test

import cats.data.Xor
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.finch.TestingMediaTypes
import com.mesosphere.cosmos.http.Authorization
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.internal.circe.Decoders._
import com.mesosphere.cosmos.internal.circe.Encoders._
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.model.Repository
import com.mesosphere.universe.{MediaTypes => UMediaTypes}
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.SimpleFilter
import com.twitter.finagle.http._
import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.Try
import io.circe.Decoder
import io.circe.Encoder
import io.circe.jawn._
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.Matchers
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CosmosIntegrationTestClient extends Matchers {

  val adminRouter: AdminRouter = {
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
      val maxDisplayWidth = 10
      val tokenDisplay = token.stripPrefix("token=").take(maxDisplayWidth)
      CosmosClient.logger.info(s"Loaded authorization token '$tokenDisplay...' from environment")
      Authorization(token)
    }
  )

  object CosmosClient {
    lazy val logger: Logger =
      LoggerFactory.getLogger("com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient")

    val uri: String = {
      val property = "com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient.uri"
      Option(System.getProperty(property))
        .getOrElse(throw new AssertionError(s"Missing system property '$property' "))
    }

    def callEndpoint[Req, Res](
      path: String,
      requestBody: Req,
      requestMediaType: MediaType,
      responseMediaType: MediaType,
      status: Status = Status.Ok,
      method: String = "POST"
    )(implicit decoder: Decoder[Res], encoder: Encoder[Req]): Xor[ErrorResponse, Res] = {
      val request = method match {
        case "POST" => CosmosRequest.post(path, requestBody, requestMediaType, responseMediaType)
        case "GET" => CosmosRequest.get(path, responseMediaType)
        case _ => throw new AssertionError(s"Unexpected HTTP method: $method")
      }

      val response = submit(request)
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

    def packageSearch(request: SearchRequest): SearchResponse = {
      val Xor.Right(response) =
        callEndpoint[SearchRequest, SearchResponse](
          "package/search",
          request,
          MediaTypes.SearchRequest,
          MediaTypes.SearchResponse
        )
      response
    }

    def packageStorageRepository: Repository = {
      val Xor.Right(response: Repository) =
        callEndpoint[Unit, Repository](
          "package/storage/repository",
          (),
          TestingMediaTypes.any,
          UMediaTypes.UniverseV3Repository,
          method = "GET"
        )
      response
    }

    def packagePublish(request: PublishRequest): PublishResponse = {
      val Xor.Right(response: PublishResponse) =
        callEndpoint[PublishRequest, PublishResponse](
          "package/publish",
          request,
          MediaTypes.PublishRequest,
          MediaTypes.PublishResponse
        )
      response
    }

    def packageRepositoryAdd(request: PackageRepositoryAddRequest): PackageRepositoryAddResponse = {
      val Xor.Right(response: PackageRepositoryAddResponse) =
        callEndpoint[PackageRepositoryAddRequest, PackageRepositoryAddResponse](
          "package/repository/add",
          request,
          MediaTypes.PackageRepositoryAddRequest,
          MediaTypes.PackageRepositoryAddResponse
        )
      response
    }

    def packageRepositoryDelete(request: PackageRepositoryDeleteRequest): PackageRepositoryDeleteResponse = {
      val Xor.Right(response: PackageRepositoryDeleteResponse) =
        callEndpoint[PackageRepositoryDeleteRequest, PackageRepositoryDeleteResponse](
          "package/repository/delete",
          request,
          MediaTypes.PackageRepositoryDeleteRequest,
          MediaTypes.PackageRepositoryDeleteResponse
        )
      response
    }

    def submit(req: CosmosRequest): Response = {
      val withPath = RequestBuilder().url(s"$uri/${req.path}")
      val withAuth = Session.authorization.fold(withPath) { auth =>
        withPath.setHeader("Authorization", auth.headerValue)
      }

      val withContentType = req.contentType.fold(withAuth)(withAuth.addHeader("Content-Type", _))
      val withAccept = req.accept.fold(withContentType)(withContentType.addHeader("Accept", _))
      val finagleReq = withAccept.build(req.method, req.body)

      Await.result(client(finagleReq))
    }

    private[this] val client = {
      Services.httpClient("cosmosIntegrationTestClient", uri, RequestLogging).get
    }

    private[this] object RequestLogging extends SimpleFilter[Request, Response] {
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
          case ("Authorization", _) => s"Authorization: ****"
          case (k, v) => s"$k: $v"
        } mkString " "
      }
    }
  }
}
