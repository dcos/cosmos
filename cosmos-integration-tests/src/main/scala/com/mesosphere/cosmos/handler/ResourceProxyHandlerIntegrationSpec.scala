package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.Requests
import com.mesosphere.cosmos.http.ResourceProxyData
import com.twitter.finagle.http.Status
import org.scalatest.Assertion
import org.scalatest.FreeSpec

final class ResourceProxyHandlerIntegrationSpec extends FreeSpec {

  "The ResourceProxyHandler should" - {
    "be able to proxy a package icon file" in {
      assertSuccess(ResourceProxyData.IconSmall)
    }

    "be able to proxy a subcommand binary" in {
      assertSuccess(ResourceProxyData.LinuxBinary)
    }
  }

  private def assertSuccess(data: ResourceProxyData): Assertion = {
    val response = Requests.packageResource(data.uri)
    assertResult(Status.Ok)(response.status)
    assertResult(Some(data.contentType))(response.contentType)
    assertResult(Some(data.contentLength.bytes))(response.contentLength)
  }

}
