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
          case Status.Conflict => failureOutput(errorNel(PackageAlreadyInstalled), Status.Conflict)
          case status if (400 until 500).contains(status.code) =>
            failureOutput(errorNel(MarathonBadResponse(status.code)), Status.InternalServerError)
          case status if (500 until 600).contains(status.code) =>
            failureOutput(errorNel(MarathonBadResponse(status.code)), Status.BadGateway)
          case _ => Ok(Json.obj())
        }
      }
  }

}
