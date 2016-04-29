package com.mesosphere.cosmos

import com.netaporter.uri.Uri
import org.scalatest.{FreeSpec, Inside}

final class ServiceClientSpec extends FreeSpec with Inside {

  "A ServiceClient" - {
    "supports an optional Authorization request header" - {
      "so that Cosmos can interact with security-enabled AdminRouters" - {
        "with baseRequestBuilder()" - {
          "header not provided" in {
            val client = new AuthorizationTestClient(None)
            val requestBuilder = client.baseRequestBuilder(Uri.parse("/foo/bar/baz"))
            assertResult(false)(requestBuilder.buildGet.headerMap.contains("Authorization"))
          }
          "header provided" in {
            val client = new AuthorizationTestClient(Some("credentials"))
            val requestBuilder = client.baseRequestBuilder(Uri.parse("/foo/bar/baz"))
            val headerOpt = requestBuilder.buildDelete.headerMap.get("Authorization")
            inside (headerOpt) { case Some(header) => assertResult("credentials")(header) }
          }
        }
      }
    }
  }

}

final class AuthorizationTestClient(authorization: Option[String])
  extends ServiceClient(Uri.parse("http://example.com"), authorization)
