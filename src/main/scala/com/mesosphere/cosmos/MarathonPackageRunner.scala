package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.model.mesos.master.MarathonApp
import com.twitter.finagle.http.Status
import com.twitter.util.Future
import io.circe.generic.auto._
import io.circe.parse.decode
import io.circe.Json

/** A [[com.mesosphere.cosmos.PackageRunner]] implementation for Marathon. */
final class MarathonPackageRunner(adminRouter: AdminRouter) extends PackageRunner {

  def launch(renderedConfig: Json): Future[MarathonApp] = {
    adminRouter.createApp(renderedConfig)
      .map { response =>
        response.status match {
          case Status.Conflict => throw PackageAlreadyInstalled()
          case status if (400 until 500).contains(status.code) =>
            throw MarathonBadResponse(status)
          case status if (500 until 600).contains(status.code) =>
            throw MarathonBadGateway(status)
          case _ =>
            decode[MarathonApp](response.contentString) match {
              case Xor.Right(appResponse) => appResponse
              case Xor.Left(parseError) => throw new CirceError(parseError)
            }
        }
      }
  }

}
