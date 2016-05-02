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
          val authorizedClient = new AuthorizationTestClient(Some("credentials"))

          "with baseRequestBuilder()" in {
            val requestBuilder = authorizedClient.baseRequestBuilder(Uri.parse("/foo/bar/baz"))
            val headerOpt = requestBuilder.buildDelete.headerMap.get(Authorization)
            inside(headerOpt) { case Some(header) => assertResult("credentials")(header) }
          }
          "with get()" in {
            val headerOpt = authorizedClient.testGet.headerMap.get(Authorization)
            inside (headerOpt) { case Some(header) => assertResult("credentials")(header) }
          }
          "with post()" in {
            val headerOpt = authorizedClient.testPost.headerMap.get(Authorization)
            inside (headerOpt) { case Some(header) => assertResult("credentials")(header) }
          }
          "with postForm()" in {
            val headerOpt = authorizedClient.testPostForm.headerMap.get(Authorization)
            inside (headerOpt) { case Some(header) => assertResult("credentials")(header) }
          }
          "with delete()" in {
            val headerOpt = authorizedClient.testDelete.headerMap.get(Authorization)
            inside (headerOpt) { case Some(header) => assertResult("credentials")(header) }
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
