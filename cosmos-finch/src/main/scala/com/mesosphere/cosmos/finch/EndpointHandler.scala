package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.RequestSession
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Status
import com.twitter.util.Future
import io.circe.Json
import io.circe.syntax._
import io.finch._

abstract class EndpointHandler[Request, Response](successStatus: Status = Status.Ok) {

  final def apply(context: EndpointContext[Request, Response]): Future[Output[Json]] = {
    apply(context.requestBody)(context.session).map { response =>
      val encodedResponse = response.asJson(context.responseEncoder.encoder)

      Output.payload(encodedResponse, successStatus)
        .withHeader(Fields.ContentType -> context.responseEncoder.mediaType.show)
    }
  }

  def apply(request: Request)(implicit session: RequestSession): Future[Response]

}
