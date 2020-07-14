package com.mesosphere.cosmos.test

import java.util.concurrent.atomic.AtomicInteger
import com.mesosphere.cosmos.http.{HttpRequest, RequestSession, TestContext}
import com.mesosphere.cosmos.{AdminRouter, AdminRouterClient, MarathonClient, MesosMasterClient, Services, Uris}
import com.twitter.conversions.storage._
import com.twitter.finagle.http._
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.{Await, Future}
import io.lemonlabs.uri.dsl._
import org.scalatest.Matchers
import org.slf4j.LoggerFactory

object CosmosIntegrationTestClient extends Matchers {

  val testContext = TestContext.fromSystemProperties()
  val uri = testContext.uri

  implicit val Session = RequestSession(testContext.token, testContext.originInfo)

  val adminRouter: AdminRouter = {
    val dcosHost: String = Uris.stripTrailingSlash(uri)
    val adminRouterUri = dcosHost
    val marathonUri = dcosHost / "marathon"
    val mesosMasterUri = dcosHost / "mesos"
    new AdminRouter(
      new AdminRouterClient(adminRouterUri, Services.adminRouterClient(adminRouterUri, 5.megabytes).get()),
      new MarathonClient(marathonUri, Services.marathonClient(marathonUri, 5.megabytes).get()),
      new MesosMasterClient(mesosMasterUri, Services.mesosClient(mesosMasterUri, 5.megabytes).get())
    )
  }

  object CosmosClient {
    lazy val logger = LoggerFactory.getLogger(getClass)

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
        headers = reqWithAuth.headers + (Fields.Host -> TestContext.extractHostFromUri(uri))
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
          "{} -> {} {} {} {}",
          c.toString,
          req.method,
          req.path,
          fmtHeaders(req.headerMap),
          fmtContent(req.contentType, req.contentString)
        )

        service(req) onSuccess { res =>
          logger.debug(
            "{} <- {} {} {} {}",
            c.toString,
            res.status.code.toString,
            res.status.reason,
            fmtHeaders(res.headerMap),
            fmtContent(res.contentType, res.contentString)
          )
        }
      }

      private def fmtHeaders(h: HeaderMap): String = {
        h.map {
          case ("Authorization", _) => s"Authorization: ****"
          case (k, v) => s"$k: $v"
        } mkString " "
      }

      private[this] def fmtContent(contentType: Option[String], contentString: String): String = {
        // All human-readable endpoints speak JSON, so look for that
        if (contentType.forall(_.contains("json"))) contentString else "[non-JSON data elided]"
      }

    }
  }

}
