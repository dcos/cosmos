package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.Requests
import com.mesosphere.cosmos.http.ResourceProxyData
import com.twitter.finagle.http.Status
import org.scalatest.Assertion
import org.scalatest.FreeSpec

final class ResourceProxyHandlerIntegrationSpec extends FreeSpec {

  "A package icon file can be proxied" in {
    assertResourceProxiedCorrectly(ResourceProxyData.IconSmall)
  }

  "A subcommand binary can be proxied" in {
    assertResourceProxiedCorrectly(ResourceProxyData.LinuxBinary)
  }

  private def assertResourceProxiedCorrectly(data: ResourceProxyData): Assertion = {
    val response = Requests.packageResource(data.uri)
    assertResult(Status.Ok)(response.status)
    assertResult(Some(data.contentType))(response.contentType)
    assertResult(Some(data.contentLength.bytes))(response.contentLength)
  }

}
