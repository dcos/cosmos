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
import com.twitter.finagle.http.RequestConfig.Yes
import com.twitter.finagle.http._
import com.twitter.io.Buf
import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.Try
import io.circe.Decoder
import io.circe.Encoder
import io.circe.jawn._
import io.circe.syntax._
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

    val property = "com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient.uri"
    private[this] val client = Try {
        Option(System.getProperty(property))
          .getOrElse(throw new AssertionError(s"Missing system property '$property' "))
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

    def doGet(path: String, accept: MediaType): Response = {
      submit(buildGet(path, accept))
    }

    def doPost[Req](
      path: String,
      requestBody: Req,
      contentType: MediaType,
      accept: MediaType
    )(implicit encoder: Encoder[Req]): Response = {
      doPost(path, requestBody, contentType.show, accept.show)
    }

    def doPost[Req](
      path: String,
      requestBody: Req,
      contentType: String,
      accept: String
    )(implicit encoder: Encoder[Req]): Response = {
      val request = buildPost(path, requestBody, contentType, accept)
      submit(request)
    }

    def doPost(
      path: String,
      requestBody: String,
      contentType: Option[String],
      accept: Option[String]
    ): Response = {
      val request = buildPost(path, requestBody, contentType, accept)
      submit(request)
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
        case "POST" => buildPost(path, requestBody, requestMediaType.show, responseMediaType.show)
        case "GET" => buildGet(path, responseMediaType)
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

    def packageList(request: ListRequest = ListRequest()): ListResponse = {
      val Xor.Right(response) =
        callEndpoint[ListRequest, ListResponse](
          "package/list",
          request,
          MediaTypes.ListRequest,
          MediaTypes.ListResponse
        )
      response
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

    def packageInstall(request: InstallRequest): InstallResponse = {
      val Xor.Right(response: InstallResponse) =
        callEndpoint[InstallRequest, InstallResponse](
          "package/install",
          request,
          MediaTypes.InstallRequest,
          MediaTypes.V1InstallResponse
        )
      response
    }

    def packageUninstall(request: UninstallRequest): UninstallResponse = {
      val Xor.Right(response: UninstallResponse) =
        callEndpoint[UninstallRequest, UninstallResponse](
          "package/uninstall",
          request,
          MediaTypes.UninstallRequest,
          MediaTypes.UninstallResponse
        )
      response
    }

    private[this] def buildGet(
      path: String,
      accept: MediaType
    ): Request = {
      requestBuilder(path)
        .addHeader("Accept", accept.show)
        .buildGet()
    }

    private[this] def buildPost[Req](
      path: String,
      requestBody: Req,
      contentType: String,
      accept: String
    )(implicit encoder: Encoder[Req]): Request = {
      buildPost(path, requestBody.asJson.noSpaces, Some(contentType), Some(accept))
    }

    private[this] def buildPost(
      path: String,
      requestBody: String,
      contentType: Option[String],
      accept: Option[String]
    ): Request = {
      val withPath = requestBuilder(path)
      val withContentType = contentType.fold(withPath)(withPath.addHeader("Content-Type", _))
      val withAccept = accept.fold(withContentType)(withContentType.addHeader("Accept", _))
      withAccept.buildPost(Buf.Utf8(requestBody))
    }

    private[this] def submit(req: Request): Response = {
      Await.result(client(req))
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
          case ("Authorization", _) => s"Authorization: ****"
          case (k, v) => s"$k: $v"
        } mkString " "
      }
    }
  }
}
