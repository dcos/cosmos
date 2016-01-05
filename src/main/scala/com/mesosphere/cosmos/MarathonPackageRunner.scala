package com.mesosphere.cosmos

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Status, Response, Request, RequestBuilder}
import com.twitter.io.Buf
import com.twitter.util.{TimeoutException, Future}
import io.finch._

/** A [[com.mesosphere.cosmos.PackageRunner]] implementation for Marathon. */
final class MarathonPackageRunner(adminRouter: Service[Request, Response]) extends PackageRunner {

  def launch(renderedConfig: String): Future[Output[String]] = {
    val request = RequestBuilder()
      .url(s"http://${Config.DcosHost}/marathon/v2/apps")
      .buildPost(Buf.Utf8(renderedConfig))

    adminRouter(request)
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
