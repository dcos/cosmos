package com.mesosphere.cosmos

import com.netaporter.uri.dsl._
import com.twitter.finagle.http.{RequestBuilder, Status}
import com.twitter.util.{Await, Return}
import org.scalatest.FreeSpec

final class ServicesIntegrationSpec extends FreeSpec {

  "Services" - {
    "adminRouterClient should" - {
      "be able to connect to an https site" in {
        val url = "https://www.google.com"
        val Return(client) = Services.adminRouterClient(url)

        val request = RequestBuilder().url(url).buildGet()
        val response = Await.result(client(request))
        assertResult(response.status)(Status.Ok)
      }
    }
  }
}
