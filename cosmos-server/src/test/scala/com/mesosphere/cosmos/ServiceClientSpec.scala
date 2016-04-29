package com.mesosphere.cosmos

import com.netaporter.uri.Uri
import com.twitter.finagle.http.Request
import io.circe.Json
import org.scalatest.{FreeSpec, Inside}

final class ServiceClientSpec extends FreeSpec with Inside {

  import ServiceClientSpec._

  "A ServiceClient" - {
    "supports an optional Authorization request header" - {
      "so that Cosmos can interact with security-enabled AdminRouters" - {
        "header not provided" - {
          val unauthorizedClient = new AuthorizationTestClient(None)

          "with baseRequestBuilder()" in {
            val requestBuilder = unauthorizedClient.baseRequestBuilder(Uri.parse("/foo/bar/baz"))
            assert(!requestBuilder.buildGet.headerMap.contains(Authorization))
          }
          "with get()" in {
            assert(!unauthorizedClient.testGet.headerMap.contains(Authorization))
          }
          "with post()" in {
            assert(!unauthorizedClient.testPost.headerMap.contains(Authorization))
          }
          "with postForm()" in {
            assert(!unauthorizedClient.testPostForm.headerMap.contains(Authorization))
          }
          "with delete()" in {
            assert(!unauthorizedClient.testDelete.headerMap.contains(Authorization))
          }
        }

        "header provided" - {
          "with baseRequestBuilder()" in {
            val client = new AuthorizationTestClient(Some("credentials"))
            val requestBuilder = client.baseRequestBuilder(Uri.parse("/foo/bar/baz"))
            val headerOpt = requestBuilder.buildDelete.headerMap.get(Authorization)
            inside(headerOpt) { case Some(header) => assertResult("credentials")(header) }
          }
        }
      }
    }
  }

}

object ServiceClientSpec {
  private[cosmos] val PathUri: Uri = Uri.parse("/foo/bar/baz")
  private val Authorization: String = "Authorization"
}

private final class AuthorizationTestClient(authorization: Option[String])
  extends ServiceClient(Uri.parse("http://example.com"), authorization) {

  def testGet: Request = get(ServiceClientSpec.PathUri)
  def testPost: Request = post(ServiceClientSpec.PathUri, Json.empty)
  def testPostForm: Request = postForm(ServiceClientSpec.PathUri, "")
  def testDelete: Request = delete(ServiceClientSpec.PathUri)

}
