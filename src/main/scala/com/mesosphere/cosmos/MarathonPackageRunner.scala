package com.mesosphere.cosmos

import com.twitter.finagle.http.Status
import com.twitter.util.{Future, TimeoutException}
import io.finch._

/** A [[com.mesosphere.cosmos.PackageRunner]] implementation for Marathon. */
final class MarathonPackageRunner(adminRouter: AdminRouter) extends PackageRunner {

  def launch(renderedConfig: String): Future[Output[String]] = {
    adminRouter.createApp(renderedConfig)
      .map { response =>
        response.status match {
          case Status.Conflict => Conflict(new Exception(s"Package is already installed"))
          case status if (400 until 500).contains(status.code) =>
            val message = s"Received response status code ${status.code} from Marathon"
            InternalServerError(new Exception(message))
          case status if (500 until 600).contains(status.code) =>
            val message = s"Received response status code ${status.code} from Marathon"
            BadGateway(new Exception(message))
          case _ => Ok("")
        }
      }
      .handle {
        case _: TimeoutException => BadGateway(new Exception("Marathon request timed out"))
        case t =>
          val message = s"Unknown Marathon request error: ${t.getMessage}"
          BadGateway(new Exception(message))
      }
  }

}
