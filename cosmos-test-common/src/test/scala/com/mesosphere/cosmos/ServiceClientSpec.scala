package com.mesosphere.cosmos

import _root_.io.circe.Json
import com.mesosphere.cosmos.http.{Authorization, RequestSession}
import com.mesosphere.cosmos.model.OriginHostScheme
import com.mesosphere.cosmos.test.TestUtil
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Request
import org.scalatest.{FreeSpec, Inside}

final class ServiceClientSpec extends FreeSpec with Inside {

  import ServiceClientSpec._

  "A ServiceClient" - {
    "supports an optional AuthorizationHeaderName request header" - {
      "so that Cosmos can interact with security-enabled AdminRouters" - {
        val testClient = new AuthorizationTestClient()
        "header not provided" - {

          implicit val session = TestUtil.Anonymous

          "with baseRequestBuilder()" in {
            val requestBuilder = testClient.baseRequestBuilder(Uri.parse("/foo/bar/baz"))
            assert(!requestBuilder.buildGet.headerMap.contains(AuthorizationHeaderName))
          }
          "with get()" in {
            assert(!testClient.testGet.headerMap.contains(AuthorizationHeaderName))
          }
          "with post()" in {
            assert(!testClient.testPost.headerMap.contains(AuthorizationHeaderName))
          }
          "with postForm()" in {
            assert(!testClient.testPostForm.headerMap.contains(AuthorizationHeaderName))
          }
          "with delete()" in {
            assert(!testClient.testDelete.headerMap.contains(AuthorizationHeaderName))
          }
        }

        "header provided" - {

          implicit val session = RequestSession(
            Some(Authorization("credentials")),
            OriginHostScheme("localhost", "http")
          )

          "with baseRequestBuilder()" in {
            val requestBuilder = testClient.baseRequestBuilder(Uri.parse("/foo/bar/baz"))
            val headerOpt = requestBuilder.buildDelete.headerMap.get(AuthorizationHeaderName)
            inside(headerOpt) { case Some(header) => assertResult("credentials")(header) }
          }
          "with get()" in {
            val headerOpt = testClient.testGet.headerMap.get(AuthorizationHeaderName)
            inside (headerOpt) { case Some(header) => assertResult("credentials")(header) }
          }
          "with post()" in {
            val headerOpt = testClient.testPost.headerMap.get(AuthorizationHeaderName)
            inside (headerOpt) { case Some(header) => assertResult("credentials")(header) }
          }
          "with postForm()" in {
            val headerOpt = testClient.testPostForm.headerMap.get(AuthorizationHeaderName)
            inside (headerOpt) { case Some(header) => assertResult("credentials")(header) }
          }
          "with delete()" in {
            val headerOpt = testClient.testDelete.headerMap.get(AuthorizationHeaderName)
            inside (headerOpt) { case Some(header) => assertResult("credentials")(header) }
          }
        }
      }
    }
  }

}

object ServiceClientSpec {
  private[cosmos] val PathUri: Uri = Uri.parse("/foo/bar/baz")
  private val AuthorizationHeaderName: String = "Authorization"
}

private final class AuthorizationTestClient
  extends ServiceClient(Uri.parse("http://example.com")) {

  def testGet(implicit session: RequestSession): Request = get(ServiceClientSpec.PathUri)
  def testPost(implicit session: RequestSession): Request = post(ServiceClientSpec.PathUri, Json.Null)
  def testPostForm(implicit session: RequestSession): Request = postForm(ServiceClientSpec.PathUri, "")
  def testDelete(implicit session: RequestSession): Request = delete(ServiceClientSpec.PathUri)

}
