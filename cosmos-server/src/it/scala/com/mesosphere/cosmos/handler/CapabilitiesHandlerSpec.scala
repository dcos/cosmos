package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.CapabilitiesResponse
import com.mesosphere.cosmos.rpc.v1.model.Capability
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient._
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Status
import io.circe.jawn._
import org.scalatest.FreeSpec
import scala.util.Right

final class CapabilitiesHandlerSpec extends FreeSpec {

  "The capabilities handler should return a document" in {
    val response = CosmosClient.submit(CosmosRequests.capabilities)

    assertResult(Status.Ok)(response.status)
    assertResult(MediaTypes.CapabilitiesResponse.show)(response.headerMap(Fields.ContentType))
    val Right(body) = decode[CapabilitiesResponse](response.contentString)
    val expected = CapabilitiesResponse(List(
      Capability("PACKAGE_MANAGEMENT"),
      Capability("SUPPORT_CLUSTER_REPORT"),
      Capability("METRONOME"),
      Capability("LOGGING")
    ))
    assertResult(expected)(body)
  }
}
