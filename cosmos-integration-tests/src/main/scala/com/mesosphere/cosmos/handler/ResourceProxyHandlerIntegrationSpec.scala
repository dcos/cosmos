package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.Requests
import com.mesosphere.cosmos.error.CosmosException
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

    "fail when the url is not in package collections" in {
      val response = intercept[CosmosException](Requests.packageResource(ResourceProxyData.thirdPartyUnknownResource.uri))
      assertResult(Status.Forbidden)(response.status)
    }

    "fail when the url returns a content length header that is not equal to the actual content" in {
      val response = intercept[CosmosException](Requests.packageResource(ResourceProxyData.knownResourceWithInvalidHeader.uri))
      assertResult(Status.InternalServerError)(response.error.exception.status)
    }
  }

  private def assertSuccess(data: ResourceProxyData): Assertion = {
    val response = Requests.packageResource(data.uri)
    assertResult(Status.Ok)(response.status)
    assertResult(Some(data.contentType))(response.contentType)
    assertResult(Some(data.contentLength.bytes))(response.contentLength)
  }

}
