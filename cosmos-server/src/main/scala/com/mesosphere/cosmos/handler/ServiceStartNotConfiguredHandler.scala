package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc.v1.model.{ServiceStartRequest, ServiceStartResponse}
import com.twitter.util.Future

private[cosmos] final class ServiceStartNotConfiguredHandler
  extends EndpointHandler[rpc.v1.model.ServiceStartRequest, rpc.v1.model.ServiceStartResponse] {
  override def apply(
    request: ServiceStartRequest
  )(implicit session: RequestSession): Future[ServiceStartResponse] = {
    Future.exception(NotImplemented("Service Start has not been configured correctly"))
  }
}
