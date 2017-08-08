package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.Requests
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.Status
import org.scalatest.Assertion
import org.scalatest.FreeSpec

final class ResourceProxyHandlerSpec extends FreeSpec {

  "A package icon file can be proxied" in {
    val iconSmallUri = "https://github.com/dcos/dcos-ui/blob/master/plugins/services/src/img" +
      "/icon-service-default-small.png?raw=true"
    val expectedContentLength = 888L

    assertResourceProxiedCorrectly(iconSmallUri, "image/png", expectedContentLength)
  }

  "A subcommand binary can be proxied" in {
    val linuxBinaryUri = "https://infinity-artifacts.s3.amazonaws.com/uninstalltestfixture/v2" +
      "/dcos-hello-world-linux"
    val expectedContentLength = 5098592L

    assertResourceProxiedCorrectly(linuxBinaryUri, "binary/octet-stream", expectedContentLength)
  }

  private def assertResourceProxiedCorrectly(
    resourceUri: Uri,
    expectedContentType: String,
    expectedContentLength: Long
  ): Assertion = {
    val response = Requests.packageResource(resourceUri)
    assertResult(Status.Ok)(response.status)
    assertResult(Some(expectedContentType))(response.contentType)
    assertResult(Some(expectedContentLength))(response.contentLength)
  }

}
