package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.RequestSession
import com.twitter.util.Future
import io.circe.Json
import io.circe.syntax._
import io.finch._

private[cosmos] abstract class EndpointHandler[Request, Response] {

  final def apply(context: EndpointContext[Request, Response]): Future[Output[Json]] = {
    apply(context.requestBody)(context.session).map { response =>
      val encodedResponse = response.asJson(context.responseEncoder.encoder)
      Ok(encodedResponse).withContentType(Some(context.responseEncoder.mediaType.show))
    }
  }

  def apply(request: Request)(implicit session: RequestSession): Future[Response]

}
