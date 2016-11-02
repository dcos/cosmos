package com.mesosphere.cosmos.handler

import cats.data.Xor
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.CapabilitiesResponse
import com.mesosphere.cosmos.rpc.v1.model.Capability
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient._
import com.mesosphere.cosmos.test.CosmosRequest
import com.twitter.finagle.http.Status
import io.circe.jawn._
import org.scalatest.FreeSpec

final class CapabilitiesHandlerSpec extends FreeSpec {

  "The capabilities handler should return a document" in {
    val request = CosmosRequest.get(path = "capabilities", accept = MediaTypes.CapabilitiesResponse)
    val response = CosmosClient.submit(request)

    assertResult(Status.Ok)(response.status)
    assertResult(MediaTypes.CapabilitiesResponse.show)(response.headerMap("Content-Type"))
    val Xor.Right(body) = decode[CapabilitiesResponse](response.contentString)
    val expected = CapabilitiesResponse(List(
      Capability("PACKAGE_MANAGEMENT"),
      Capability("SUPPORT_CLUSTER_REPORT"),
      Capability("METRONOME")
    ))
    assertResult(expected)(body)
  }
}
