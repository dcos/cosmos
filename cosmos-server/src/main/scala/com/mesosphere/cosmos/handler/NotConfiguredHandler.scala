package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.error.NotImplemented
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.twitter.util.Future

private[cosmos] final class NotConfiguredHandler[Req, Res](operationName: String)
  extends EndpointHandler[Req, Res] {
  override def apply(
    unused: Req
  )(implicit session: RequestSession): Future[Res] = {
    Future.exception(NotImplemented(operationName).exception)
  }
}
