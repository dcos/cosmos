package com.mesosphere.cosmos

import com.netaporter.uri.dsl._
import com.twitter.util.{Return, Throw}
import org.scalatest.FreeSpec

class UrisSpec extends FreeSpec {

  "extractHostAndPort should" - {
    "succeed for" - {
      "http://domain" in {
        assertResult(Return(ConnectionDetails("domain", 80, tls = false)))(Uris.extractHostAndPort("http://domain"))
      }
      "https://domain" in {
        assertResult(Return(ConnectionDetails("domain", 443, tls = true)))(Uris.extractHostAndPort("https://domain"))
      }
      "http://domain:8080" in {
        assertResult(Return(ConnectionDetails("domain", 8080, tls = false)))(Uris.extractHostAndPort("http://domain:8080"))
      }
      "http://sub.domain" in {
        assertResult(Return(ConnectionDetails("sub.domain", 80, tls = false)))(Uris.extractHostAndPort("http://sub.domain"))
      }
      "https://sub.domain" in {
        assertResult(Return(ConnectionDetails("sub.domain", 443, tls = true)))(Uris.extractHostAndPort("https://sub.domain"))
      }
      "http://10.0.0.1" in {
        assertResult(Return(ConnectionDetails("10.0.0.1", 80, tls = false)))(Uris.extractHostAndPort("http://10.0.0.1"))
      }
      "https://10.0.0.1" in {
        assertResult(Return(ConnectionDetails("10.0.0.1", 443, tls = true)))(Uris.extractHostAndPort("https://10.0.0.1"))
      }
    }
    "fail for" - {
      "domain" in {
        val Throw(err) = Uris.extractHostAndPort("domain")
        val expectedMessage = "Unsupported or invalid URI. Expected format 'http[s]://example.com[:port][/base-path]' actual '/domain'"
        assertResult(expectedMessage)(err.getMessage)
      }
      "domain:8080" in {
        val Throw(err) = Uris.extractHostAndPort("domain:8080")
        val expectedMessage = "Unsupported or invalid URI. Expected format 'http[s]://example.com[:port][/base-path]' actual '/domain:8080'"
        assertResult(expectedMessage)(err.getMessage)
      }
      "ftp://domain" in {
        val Throw(err) = Uris.extractHostAndPort("ftp://domain")
        val expectedMessage = "Unsupported or invalid URI. Expected format 'http[s]://example.com[:port][/base-path]' actual 'ftp://domain'"
        assertResult(expectedMessage)(err.getMessage)
      }
    }
  }

}
