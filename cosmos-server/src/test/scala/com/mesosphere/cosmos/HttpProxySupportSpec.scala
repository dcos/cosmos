package com.mesosphere.cosmos

import java.net.{Authenticator, URI, URL}
import java.net.Authenticator.RequestorType

import com.mesosphere.cosmos.HttpProxySupport.{CosmosHttpProxyPasswordAuthenticator, ProxyEnvVariables}
import org.scalatest.FreeSpec
import com.netaporter.uri.dsl._

class HttpProxySupportSpec extends FreeSpec {

  "HttpProxySupport should" - {

    "allow proxy configuration for" - {

      "HTTP" - {

        "respects http.proxyHost and http.proxyPort" in {
          System.setProperty("http.proxyHost", "host")
          System.setProperty("http.proxyPort", "port")
          HttpProxySupport.initHttpProxyProperties(Some("http://localhost:3128"), "http.proxyHost", "http.proxyPort")
          assertResult("host")(System.getProperty("http.proxyHost"))
          assertResult("port")(System.getProperty("http.proxyPort"))
          clearSystemProperty("http.proxyHost")
          clearSystemProperty("http.proxyPort")
        }

        "http.proxyHost and http.proxyPort will be set if not previously set" in {
          HttpProxySupport.initHttpProxyProperties(Some("http://localhost:3128"), "http.proxyHost", "http.proxyPort")
          assertResult("localhost")(System.getProperty("http.proxyHost"))
          assertResult("3128")(System.getProperty("http.proxyPort"))
          clearSystemProperty("http.proxyHost")
          clearSystemProperty("http.proxyPort")
        }

        "will use http_proxy before HTTP_PROXY" in {
          val env = Map(
            "http_proxy" -> "http://localhost:3128",
            "HTTP_PROXY" -> "http://hostlocal:8213"
          )
          val vars = HttpProxySupport.extractProxyEnvVariables(env)
          assertResult(ProxyEnvVariables(Some("http://localhost:3128"), None, None))(vars)
        }

        "will use HTTP_PROXY if http_proxy not present" in {
          val env = Map(
            "HTTP_PROXY" -> "http://hostlocal:8213"
          )
          val vars = HttpProxySupport.extractProxyEnvVariables(env)
          assertResult(ProxyEnvVariables(Some("http://hostlocal:8213"), None, None))(vars)
        }
        
      }

      "HTTPS" - {

        "respects https.proxyHost and https.proxyPort" in {
          System.setProperty("https.proxyHost", "host")
          System.setProperty("https.proxyPort", "port")
          HttpProxySupport.initHttpProxyProperties(Some("https://localhost:3128"), "https.proxyHost", "https.proxyPort")
          assertResult("host")(System.getProperty("https.proxyHost"))
          assertResult("port")(System.getProperty("https.proxyPort"))
          clearSystemProperty("https.proxyHost")
          clearSystemProperty("https.proxyPort")
        }

        "https.proxyHost and https.proxyPort will be set if not previously set" in {
          HttpProxySupport.initHttpProxyProperties(Some("https://localhost:3128"), "https.proxyHost", "https.proxyPort")
          assertResult("localhost")(System.getProperty("https.proxyHost"))
          assertResult("3128")(System.getProperty("https.proxyPort"))
          clearSystemProperty("https.proxyHost")
          clearSystemProperty("https.proxyPort")
        }

        "will use https_proxy before HTTPS_PROXY" in {
          val env = Map(
            "https_proxy" -> "http://localhost:3128",
            "HTTPS_PROXY" -> "http://hostlocal:8213"
          )
          val vars = HttpProxySupport.extractProxyEnvVariables(env)
          assertResult(ProxyEnvVariables(None, Some("http://localhost:3128"), None))(vars)
        }

        "will use HTTPS_PROXY if https_proxy not present" in {
          val env = Map(
            "HTTPS_PROXY" -> "http://hostlocal:8213"
          )
          val vars = HttpProxySupport.extractProxyEnvVariables(env)
          assertResult(ProxyEnvVariables(None, Some("http://hostlocal:8213"), None))(vars)
        }

      }

      "No Proxy" - {

        "respects http.nonProxyHosts" in {
          System.setProperty("http.nonProxyHosts", "*.google.com")
          HttpProxySupport.initNoProxyProperties(Some("something"))
          assertResult("*.google.com")(System.getProperty("http.nonProxyHosts"))
          clearSystemProperty("http.nonProxyHosts")
        }

        "sets http.nonProxyHosts from env variable" in {
          HttpProxySupport.initNoProxyProperties(Some("something"))
          assertResult("something")(System.getProperty("http.nonProxyHosts"))
          clearSystemProperty("http.nonProxyHosts")
        }

        "will use no_proxy before NO_PROXY" in {
          val env = Map(
            "no_proxy" -> "no_proxy_val",
            "NO_PROXY" -> "val_proxy_no"
          )
          val vars = HttpProxySupport.extractProxyEnvVariables(env)
          assertResult(ProxyEnvVariables(None, None, Some("no_proxy_val")))(vars)
        }

        "will use NO_PROXY if no_proxy is not set" in {
          val env = Map(
            "NO_PROXY" -> "val_proxy_no"
          )
          val vars = HttpProxySupport.extractProxyEnvVariables(env)
          assertResult(ProxyEnvVariables(None, None, Some("val_proxy_no")))(vars)
        }

        "should allow excluded hosts to be separated by ','" in {
          HttpProxySupport.initNoProxyProperties(Some("127.0.0.1,localhost"))
          assertResult("127.0.0.1|localhost")(System.getProperty("http.nonProxyHosts"))
          clearSystemProperty("http.nonProxyHosts")
        }

        "should allow excluded hosts to be separated by '|'" in {
          HttpProxySupport.initNoProxyProperties(Some("127.0.0.1|localhost"))
          assertResult("127.0.0.1|localhost")(System.getProperty("http.nonProxyHosts"))
          clearSystemProperty("http.nonProxyHosts")
        }

      }
      
      "Proxy Credentials" - {

        val vars = ProxyEnvVariables(Some("http://localhost:3128"), Some("http://hostlocal:8213"), Some("no_proxy_vals"))
        val authVars = ProxyEnvVariables(Some("http://test:testtest@localhost:3128"), Some("http://foo:bar@hostlocal:8213"), Some("no_proxy_vals"))

        "can be set from http_proxy, https_proxy and no_proxy" in {
          var authenticatorSet = false
          val setAuthenticator = (a: Authenticator) => { authenticatorSet = true }

          HttpProxySupport.initProxyConfig(vars, setAuthenticator)

          assertResult("localhost")(System.getProperty("http.proxyHost"))
          assertResult("3128")(System.getProperty("http.proxyPort"))
          assertResult("hostlocal")(System.getProperty("https.proxyHost"))
          assertResult("8213")(System.getProperty("https.proxyPort"))
          assertResult("no_proxy_vals")(System.getProperty("http.nonProxyHosts"))
          assert(authenticatorSet)

          clearSystemProperty("http.proxyHost")
          clearSystemProperty("http.proxyPort")
          clearSystemProperty("http.nonProxyHosts")
          clearSystemProperty("https.proxyHost")
          clearSystemProperty("https.proxyPort")
          Authenticator.setDefault(null)
        }

        "are used in Authenticator (http)" - {
          val auth = new CosmosHttpProxyPasswordAuthenticator(authVars) {
            override def getRequestorType: RequestorType = RequestorType.PROXY
            override def getRequestingURL: URL = URI.create("http://someplace:123").toURL
          }

          val authentication = auth.getPasswordAuthentication

          assert(authentication != null)
          assertResult("test")(authentication.getUserName)
          assertResult("testtest".toCharArray)(authentication.getPassword)

        }

        "are used in Authenticator (https)" - {
          val auth = new CosmosHttpProxyPasswordAuthenticator(authVars) {
            override def getRequestorType: RequestorType = RequestorType.PROXY
            override def getRequestingURL: URL = URI.create("https://someplace:123").toURL
          }

          val authentication = auth.getPasswordAuthentication

          assert(authentication != null)
          assertResult("foo")(authentication.getUserName)
          assertResult("bar".toCharArray)(authentication.getPassword)
        }

        "are not provided when non http or https" in {
          val auth = new CosmosHttpProxyPasswordAuthenticator(authVars) {
            override def getRequestorType: RequestorType = RequestorType.PROXY
            override def getRequestingURL: URL = URI.create("ftp://someplace:123").toURL
          }

          val authentication = auth.getPasswordAuthentication
          assert(authentication == null)
        }

        "are not provided when requestorType is not proxy" in {
          val auth = new CosmosHttpProxyPasswordAuthenticator(authVars) {
            override def getRequestorType: RequestorType = RequestorType.SERVER
          }

          val authentication = auth.getPasswordAuthentication
          assert(authentication == null)
        }

        "are not provided when no user:pass in url" in {
          val auth = new CosmosHttpProxyPasswordAuthenticator(vars) {
            override def getRequestorType: RequestorType = RequestorType.PROXY
            override def getRequestingURL: URL = URI.create("https://someplace:123").toURL
          }

          val authentication = auth.getPasswordAuthentication
          assert(authentication == null)
        }

        "are not provided when no password in url" in {
          val vars = ProxyEnvVariables(Some("http://bill@localhost:3128"), Some("http://alice@hostlocal:8213"), Some("no_proxy_vals"))
          val auth = new CosmosHttpProxyPasswordAuthenticator(vars) {
            override def getRequestorType: RequestorType = RequestorType.PROXY
            override def getRequestingURL: URL = URI.create("https://someplace:123").toURL
          }

          val authentication = auth.getPasswordAuthentication
          assert(authentication == null)
        }

      }

    }

  }

  private[this] def clearSystemProperty(name: String): Unit = {
    val _ = System.clearProperty(name)
  }

}
