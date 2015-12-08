package com.mesosphere.cosmos

import com.twitter.finagle.Service
import com.twitter.finagle.http._
import io.finch.test.ServiceIntegrationSuite
import org.scalatest.fixture

class CosmosSpec extends fixture.FlatSpec with ServiceIntegrationSuite {

  def createService(): Service[Request, Response] = {
    Cosmos.ping.toService
  }

  "cosmos" should "respond to ping" in { f =>
    val request = Request(Method.Get, "/ping")
    val response = f(request)
    assertResult(200)(response.statusCode)
    assertResult("pong")(response.contentString)
  }

}
