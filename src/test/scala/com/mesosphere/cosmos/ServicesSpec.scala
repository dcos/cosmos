package com.mesosphere.cosmos

import com.mesosphere.cosmos.Services.ConnectionDetails
import com.netaporter.uri.dsl._
import com.twitter.finagle.NoBrokersAvailableException
import com.twitter.finagle.http._
import com.twitter.util.{Await, Throw, Return}
import org.scalatest.FreeSpec

final class ServicesSpec extends FreeSpec {

  "Services" - {

    "adminRouterClient should" - {
      "be able to be created with a well formed URI with a domain that doesn't resolve" in {
        val url = "http://some.domain.that-im-pretty-sure-doesnt-exist-anywhere"
        val Return(client) = Services.adminRouterClient(url)

        try {
          val request = RequestBuilder().url(url).buildGet()
          val response = Await.result(client(request))
          assertResult(response.status)(Status.ServiceUnavailable)
        } catch {
          case e: NoBrokersAvailableException => // Success
        } finally {
          val _ = Await.ready(client.close())
        }
      }
    }

    "extractHostAndPort should" - {
      "succeed for" - {
        "http://domain" in {
          assertResult(Return(ConnectionDetails("domain", 80, tls = false)))(Services.extractHostAndPort("http://domain"))
        }
        "https://domain" in {
          assertResult(Return(ConnectionDetails("domain", 443, tls = true)))(Services.extractHostAndPort("https://domain"))
        }
        "http://domain:8080" in {
          assertResult(Return(ConnectionDetails("domain", 8080, tls = false)))(Services.extractHostAndPort("http://domain:8080"))
        }
        "http://sub.domain" in {
          assertResult(Return(ConnectionDetails("sub.domain", 80, tls = false)))(Services.extractHostAndPort("http://sub.domain"))
        }
        "https://sub.domain" in {
          assertResult(Return(ConnectionDetails("sub.domain", 443, tls = true)))(Services.extractHostAndPort("https://sub.domain"))
        }
        "http://10.0.0.1" in {
          assertResult(Return(ConnectionDetails("10.0.0.1", 80, tls = false)))(Services.extractHostAndPort("http://10.0.0.1"))
        }
        "https://10.0.0.1" in {
          assertResult(Return(ConnectionDetails("10.0.0.1", 443, tls = true)))(Services.extractHostAndPort("https://10.0.0.1"))
        }
      }
      "fail for" - {
        "domain" in {
          val Throw(err) = Services.extractHostAndPort("domain")
          val expectedMessage = "Unsupported or invalid URI. Expected format 'http[s]://example.com[:port][/base-path]' actual '/domain'"
          assertResult(expectedMessage)(err.getMessage)
        }
        "domain:8080" in {
          val Throw(err) = Services.extractHostAndPort("domain:8080")
          val expectedMessage = "Unsupported or invalid URI. Expected format 'http[s]://example.com[:port][/base-path]' actual '/domain:8080'"
          assertResult(expectedMessage)(err.getMessage)
        }
        "ftp://domain" in {
          val Throw(err) = Services.extractHostAndPort("ftp://domain")
          val expectedMessage = "Unsupported or invalid URI. Expected format 'http[s]://example.com[:port][/base-path]' actual 'ftp://domain'"
          assertResult(expectedMessage)(err.getMessage)
        }
      }
    }
  }
}
