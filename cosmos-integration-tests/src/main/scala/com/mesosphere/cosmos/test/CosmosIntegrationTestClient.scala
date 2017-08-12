package com.mesosphere.cosmos.test

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.AdminRouterClient
import com.mesosphere.cosmos.MarathonClient
import com.mesosphere.cosmos.MesosMasterClient
import com.mesosphere.cosmos.Services
import com.mesosphere.cosmos.Trys
import com.mesosphere.cosmos.Uris
import com.mesosphere.cosmos.dcosUri
import com.mesosphere.cosmos.http.Authorization
import com.mesosphere.cosmos.http.HttpRequest
import com.mesosphere.cosmos.http.RequestSession
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.conversions.storage._
import com.twitter.finagle.Service
import com.twitter.finagle.SimpleFilter
import com.twitter.finagle.http._
import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.Try
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.Matchers
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
          Services.adminRouterClient(adminRouterUri, 5.megabytes).map { adminRouterUri -> _},
          Services.marathonClient(marathonUri, 5.megabytes).map { marathonUri -> _ },
          Services.mesosClient(mesosMasterUri, 5.megabytes).map { mesosMasterUri -> _ }
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
    lazy val logger = LoggerFactory.getLogger(getClass())

    val uri: Uri = getClientProperty("CosmosClient", "uri")

    /** Ensures that we create Finagle requests correctly.
      *
      * Specifically, prevents confusion around the `uri` parameter of
      * [[com.twitter.finagle.http.Request.apply()]], which should be the relative path of the
      * endpoint, and not the full HTTP URI.
      *
      * It also includes the Authorization header if provided by configuration.
      */
    def submit(req: HttpRequest): Response = {
      val reqWithAuth = Session.authorization match {
        case Some(auth) =>
          req.copy(headers = req.headers + (Fields.Authorization -> auth.headerValue))
        case _ =>
          req
      }

      val reqWithAuthAndHost = reqWithAuth.copy(
        headers = reqWithAuth.headers + (Fields.Host -> uri.host.get)
      )

      val finagleReq = HttpRequest.toFinagle(reqWithAuthAndHost)
      Await.result(client(finagleReq))
    }

    // Do not relax the visibility on this -- use `submit()` instead; see its Scaladoc for why
    private[this] val client = {
      logger.debug(s"Configuring integration test client for $uri")
      Services.httpClient("cosmosIntegrationTestClient", uri, 5.megabytes, RequestLogging).get
    }

    private[this] object RequestLogging extends SimpleFilter[Request, Response] {
      val counter = new AtomicInteger()
      override def apply(req: Request, service: Service[Request, Response]): Future[Response] = {
        val c = counter.getAndIncrement
        logger.debug(
          s"$c -> ${req.method} ${req.path} ${fmtHeaders(req.headerMap)} ${req.contentString}"
        )
        service(req) map { res =>
          logger.debug(
            s"$c <- ${res.status.code} ${res.status.reason} " +
            s"${fmtHeaders(res.headerMap)} ${res.contentString}"
          )
          res
        }
      }

      private def fmtHeaders(h: HeaderMap): String = {
        h.map {
          case ("Authorization", _) => s"Authorization: ****"
          case (k, v) => s"$k: $v"
        } mkString " "
      }
    }
  }

  private def getClientProperty(clientName: String, key: String): String = {
    val property = s"com.mesosphere.cosmos.test.CosmosIntegrationTestClient.$clientName.$key"
      Option(System.getProperty(property))
        .getOrElse(throw new AssertionError(s"Missing system property '$property' "))
  }

}
