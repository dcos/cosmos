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
import com.twitter.finagle.http.Status
import com.twitter.util.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

final class MarathonPackageRunner(adminRouter: AdminRouter) {

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
            Try(decode[MarathonError](response.contentString)) match {
              case Success(marathonError) =>
                throw MarathonBadResponse(marathonError).exception
              case Failure(_) =>
                throw MarathonGenericError(status).exception
            }
          case status if (500 until 600).contains(status.code) =>
            throw MarathonBadGateway(status).exception
          case _ =>
            decode[MarathonApp](response.contentString)
        }
      }
  }

}
