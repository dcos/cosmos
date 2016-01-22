package com.mesosphere.cosmos

import com.twitter.finagle.http.Status
import com.twitter.util.Future
import io.circe.Json
import io.finch._

/** A [[com.mesosphere.cosmos.PackageRunner]] implementation for Marathon. */
final class MarathonPackageRunner(adminRouter: AdminRouter) extends PackageRunner {

  def launch(renderedConfig: Json): Future[Output[Json]] = {
    adminRouter.createApp(renderedConfig)
      .map { response =>
        response.status match {
          case Status.Conflict => throw PackageAlreadyInstalled()
          case status if (400 until 500).contains(status.code) =>
            throw MarathonBadResponse(status)
          case status if (500 until 600).contains(status.code) =>
            throw MarathonBadGateway(status)
          case _ => Ok(Json.obj())
        }
      }
  }

}
