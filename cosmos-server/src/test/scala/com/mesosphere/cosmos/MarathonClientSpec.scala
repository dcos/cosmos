package com.mesosphere.cosmos

import com.netaporter.uri.Uri
import com.twitter.finagle.http.service.NullService
import org.scalatest.{FreeSpec, Inside}

final class MarathonClientSpec extends FreeSpec with Inside {

  "A MarathonClient" - {
    "supports an optional Authorization request header" - {
      "so that Cosmos can interact with security-enabled AdminRouters" - {
        "with baseRequestBuilder()" - {
          val uri = Uri.parse("http://example.com")

          "header not provided" in {
            val unauthorizedClient = new MarathonClient(uri, NullService, authorization = None)
            val requestBuilder = unauthorizedClient.baseRequestBuilder(Uri.parse("/foo/bar/baz"))
            assert(!requestBuilder.buildGet.headerMap.contains("Authorization"))
          }
          "header provided" in {
            val authorizedClient =
              new MarathonClient(uri, NullService, authorization = Some("credentials"))
            val requestBuilder = authorizedClient.baseRequestBuilder(Uri.parse("/foo/bar/baz"))
            val headerOpt = requestBuilder.buildDelete.headerMap.get("Authorization")
            inside (headerOpt) { case Some(header) => assertResult("credentials")(header) }
          }
        }
      }
    }
  }

}
