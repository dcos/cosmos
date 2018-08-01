package com.mesosphere.cosmos

import _root_.io.circe.JsonObject
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.error.MarathonBadGateway
import com.mesosphere.cosmos.error.MarathonBadResponse
import com.mesosphere.cosmos.error.MarathonGenericError
import com.mesosphere.cosmos.error.ServiceAlreadyStarted
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonError
import com.mesosphere.error.ResultOps
import com.twitter.finagle.http.Status
import com.twitter.util.Future
import io.netty.handler.codec.http.HttpResponseStatus
import org.slf4j.Logger
import scala.util.Failure
import scala.util.Success
import scala.util.Try

final class MarathonPackageRunner(adminRouter: AdminRouter) {

  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  /** Execute the package described by the given JSON configuration.
   *
   * @param renderedConfig the fully-specified configuration of the package to run
   * @return The response from Marathon, if the request was successful.
   */
  def launch(renderedConfig: JsonObject)(implicit session: RequestSession): Future[MarathonApp] = {
    adminRouter.createApp(renderedConfig)
      .map { response =>
        response.status match {
          case Status.Conflict =>
            throw ServiceAlreadyStarted().exception
          case status if (400 until 500).contains(status.code) =>
            logger.warn(s"Marathon returned [${status.code}]: ${response.contentString}")
            Try(decode[MarathonError](response.contentString).getOrThrow) match {
              case Success(marathonError) =>
                throw MarathonBadResponse(marathonError).exception
              case Failure(_) =>
                throw MarathonGenericError(HttpResponseStatus.valueOf(status.code)).exception
            }
          case status if (500 until 600).contains(status.code) =>
            logger.warn(s"Marathon returned [${status.code}]: ${response.contentString}")
            throw MarathonBadGateway(HttpResponseStatus.valueOf(status.code)).exception
          case _ =>
            decode[MarathonApp](response.contentString).getOrThrow
        }
      }
  }

}
