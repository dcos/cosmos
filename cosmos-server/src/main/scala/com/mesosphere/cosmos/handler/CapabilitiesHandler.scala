package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc.v1.model.{CapabilitiesResponse, Capability}
import com.twitter.util.Future

private[cosmos] final class CapabilitiesHandler
  extends EndpointHandler[Unit, CapabilitiesResponse] {

  private[this] val response = CapabilitiesResponse(
    List(Capability("PACKAGE_MANAGEMENT"), Capability("SUPPORT_CLUSTER_REPORT"), Capability("METRONOME")))

  override def apply(v1: Unit)(implicit
    session: RequestSession
  ): Future[CapabilitiesResponse] = {
    Future.value(response)
  }

}
