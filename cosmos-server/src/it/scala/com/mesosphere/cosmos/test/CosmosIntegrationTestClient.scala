package com.mesosphere.cosmos.test

import cats.data.Xor
import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.AdminRouterClient
import com.mesosphere.cosmos.MarathonClient
import com.mesosphere.cosmos.MesosMasterClient
import com.mesosphere.cosmos.ObjectStorageUri
import com.mesosphere.cosmos.Services
import com.mesosphere.cosmos.Trys
import com.mesosphere.cosmos.Uris
import com.mesosphere.cosmos.dcosUri
import com.mesosphere.cosmos.http.Authorization
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.internal.circe.Decoders._
import com.mesosphere.cosmos.internal.circe.Encoders._
import com.mesosphere.cosmos.model.ZooKeeperUri
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
import com.twitter.finagle.http.Version.Http11
import com.twitter.finagle.http._
import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.Try
import io.circe.Decoder
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

    val uri: String = getClientProperty("CosmosClient", "uri")

    def callEndpoint[Res](request: CosmosRequest, expectedStatus: Status = Status.Ok)(implicit
      decoder: Decoder[Res]
    ): ErrorResponse Xor Res = {
      val response = submit(request)
      assertResult(expectedStatus)(response.status)

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

    def packageSearch(requestBody: SearchRequest): SearchResponse = {
      val request = CosmosRequest.post(
        "package/search",
        requestBody,
        MediaTypes.SearchRequest,
        MediaTypes.SearchResponse
      )
      val Xor.Right(response) = callEndpoint[SearchResponse](request)
      response
    }

    def packageStorageRepository: Repository = {
      val request = CosmosRequest.get(
        "package/storage/repository",
        UMediaTypes.UniverseV3Repository
      )
      val Xor.Right(response) = callEndpoint[Repository](request)
      response
    }

    def packagePublish(requestBody: PublishRequest): PublishResponse = {
      val request = CosmosRequest.post(
        "package/publish",
        requestBody,
        MediaTypes.PublishRequest,
        MediaTypes.PublishResponse
      )
      val Xor.Right(response) = callEndpoint[PublishResponse](request)
      response
    }

    def packageRepositoryAdd(requestBody: PackageRepositoryAddRequest): PackageRepositoryAddResponse = {
      val request = CosmosRequest.post(
        "package/repository/add",
        requestBody,
        MediaTypes.PackageRepositoryAddRequest,
        MediaTypes.PackageRepositoryAddResponse
      )
      val Xor.Right(response) = callEndpoint[PackageRepositoryAddResponse](request)
      response
    }

    def packageRepositoryDelete(requestBody: PackageRepositoryDeleteRequest): PackageRepositoryDeleteResponse = {
      val request = CosmosRequest.post(
        "package/repository/delete",
        requestBody,
        MediaTypes.PackageRepositoryDeleteRequest,
        MediaTypes.PackageRepositoryDeleteResponse
      )
      val Xor.Right(response) = callEndpoint[PackageRepositoryDeleteResponse](request)
      response
    }

    /** Ensures that we create Finagle requests correctly.
      *
      * Specifically, prevents confusion around the `uri` parameter of
      * [[com.twitter.finagle.http.Request.apply()]], which should be the relative path of the
      * endpoint, and not the full HTTP URI.
      *
      * It also includes the Authorization header if provided by configuration.
      */
    def submit(req: CosmosRequest): Response = {
      val finagleReq = buildRequest(req)
      Await.result(client(finagleReq))
    }

    private[this] def buildRequest(cosmosRequest: CosmosRequest): Request = {
      val pathPrefix = if (cosmosRequest.path.startsWith("/")) "" else "/"
      val absolutePath = pathPrefix + cosmosRequest.path

      val finagleRequest = cosmosRequest.body match {
        case NoBody =>
          Request(absolutePath)
        case Monolithic(buf) =>
          val req = Request(Method.Post, absolutePath)
          req.content = buf
          req
        case Chunked(reader) =>
          Request(Http11, cosmosRequest.method, absolutePath, reader)
      }

      finagleRequest.headerMap ++= cosmosRequest.customHeaders
      cosmosRequest.accept.foreach(finagleRequest.accept = _)
      cosmosRequest.contentType.foreach(finagleRequest.contentType = _)
      Session.authorization.foreach(auth => finagleRequest.authorization = auth.headerValue)
      finagleRequest
    }

    // Do not relax the visibility on this -- use `submit()` instead; see its Scaladoc for why
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

  object ZooKeeperClient {
    val uri: ZooKeeperUri = {
      ZooKeeperUri.parse(
        getClientProperty("ZooKeeperClient", "uri")
      ).get()
    }
  }

  object PackageStorageClient {

    val addedUri: ObjectStorageUri = {
      ObjectStorageUri.parse(getClientProperty("PackageStorageClient", "addedUri")).get()
    }

    val stagedUri: ObjectStorageUri = {
      ObjectStorageUri.parse(getClientProperty("PackageStorageClient", "stagedUri")).get()
    }

  }

  private[this] def getClientProperty(clientName: String, key: String): String = {
    val property = s"com.mesosphere.cosmos.test.CosmosIntegrationTestClient.$clientName.$key"
      Option(System.getProperty(property))
        .getOrElse(throw new AssertionError(s"Missing system property '$property' "))
  }

}
