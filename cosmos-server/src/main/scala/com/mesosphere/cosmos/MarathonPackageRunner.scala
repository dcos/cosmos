package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.thirdparty.marathon.model.{MarathonApp, MarathonError}
import com.mesosphere.cosmos.thirdparty.marathon.circe.Decoders._
import com.twitter.finagle.http.Status
import com.twitter.util.Future
import com.mesosphere.cosmos.http.RequestSession
import io.circe.jawn.decode
import io.circe.Json

/** A [[com.mesosphere.cosmos.PackageRunner]] implementation for Marathon. */
final class MarathonPackageRunner(adminRouter: AdminRouter) extends PackageRunner {

  import MarathonPackageRunner._

  def launch(renderedConfig: Json)(implicit session: RequestSession): Future[MarathonApp] = {
    adminRouter.createApp(renderedConfig)
      .map { response =>
        response.status match {
          case Status.Conflict =>
            throw PackageAlreadyInstalled()
          case status if (400 until 500).contains(status.code) =>
            throw clientError(response.contentString, status)
          case status if (500 until 600).contains(status.code) =>
            throw MarathonBadGateway(status)
          case _ =>
            decodeApp(response.contentString)
        }
      }
  }

}

object MarathonPackageRunner {

  def clientError[A](responseBody: String, status: Status): CosmosError = {
    decode[MarathonError](responseBody) match {
      case Xor.Right(marathonError) => MarathonBadResponse(marathonError)
      case Xor.Left(parseError) => MarathonGenericError(status)
    }
  }

  def decodeApp(responseBody: String): MarathonApp = {
    decode[MarathonApp](responseBody) match {
      case Xor.Right(appResponse) => appResponse
      case Xor.Left(parseError) => throw CirceError(parseError)
    }
  }

}
