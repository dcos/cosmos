package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.RequestSession
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Status
import com.twitter.util.Future
import com.twitter.util.Throw
import io.circe.Json
import io.circe.syntax._
import io.finch._

abstract class EndpointHandler[Request, Response](successStatus: Status = Status.Ok) {

  private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  final def apply(context: EndpointContext[Request, Response]): Future[Output[Json]] = {
    apply(context.requestBody)(context.session)
      .respond {
        case Throw(e) =>
          logger.warn(s"Processing [${context.requestBody}] resulted in : ${e.getMessage}")
        case _ => ()
      }
      .map { response =>
        Output
          .payload(response.asJson(context.responseEncoder.encoder), successStatus)
          .withHeader(Fields.ContentType -> context.responseEncoder.mediaType(response).show)
      }
  }

  def apply(request: Request)(implicit session: RequestSession): Future[Response]

}
