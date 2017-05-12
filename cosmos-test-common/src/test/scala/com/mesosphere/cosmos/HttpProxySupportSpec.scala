package com.mesosphere.cosmos

import com.mesosphere.cosmos.HttpProxySupport.CosmosHttpProxyPasswordAuthenticator
import com.mesosphere.cosmos.HttpProxySupport.ProxyEnvVariables
import com.netaporter.uri.dsl._
import java.net.Authenticator
import java.net.Authenticator.RequestorType
import java.net.URI
import java.net.URL
import org.scalatest.BeforeAndAfter
import org.scalatest.FreeSpec

final class HttpProxySupportSpec extends FreeSpec with BeforeAndAfter {

  import HttpProxySupportSpec._

  after {
    List(HttpHost, HttpPort, HttpsHost, HttpsPort, NonHosts).foreach(System.clearProperty)
    Authenticator.setDefault(null)  // scalastyle:ignore null
  }

  "HttpProxySupport should" - {

    "allow proxy configuration for" - {

      "HTTP" - {

        "parses property values" in {
          val proxyUri = "http://localhost:3128"
          val parsed = HttpProxySupport.parseHostPortProperties(HttpHost, HttpPort)(proxyUri)
          assertResult(Map(HttpHost -> "localhost", HttpPort -> "3128"))(parsed.toMap)
        }

        "respects http.proxyHost and http.proxyPort" in {
          System.setProperty(HttpHost, "host")
          System.setProperty(HttpPort, "port")

          val httpProxyUri = "http://localhost:3128"
          val proxyEnvVariables = ProxyEnvVariables(Some(httpProxyUri), None, None)
          HttpProxySupport.initProxyConfig(proxyEnvVariables, _ => ())

          assertResult("host")(System.getProperty(HttpHost))
          assertResult("port")(System.getProperty(HttpPort))
        }

        "http.proxyHost and http.proxyPort will be set if not previously set" in {
          val httpProxyUri = "http://localhost:3128"
          val proxyEnvVariables = ProxyEnvVariables(Some(httpProxyUri), None, None)
          HttpProxySupport.initProxyConfig(proxyEnvVariables, _ => ())

          assertResult("localhost")(System.getProperty(HttpHost))
          assertResult("3128")(System.getProperty(HttpPort))
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

        "parses property values" in {
          val proxyUri = "https://localhost:3128"
          val parsed = HttpProxySupport.parseHostPortProperties(HttpsHost, HttpsPort)(proxyUri)
          assertResult(Map(HttpsHost -> "localhost", HttpsPort -> "3128"))(parsed.toMap)
        }

        "respects https.proxyHost and https.proxyPort" in {
          System.setProperty(HttpsHost, "host")
          System.setProperty(HttpsPort, "port")

          val httpsProxyUri = "https://localhost:3128"
          val proxyEnvVariables = ProxyEnvVariables(None, Some(httpsProxyUri), None)
          HttpProxySupport.initProxyConfig(proxyEnvVariables, _ => ())

          assertResult("host")(System.getProperty(HttpsHost))
          assertResult("port")(System.getProperty(HttpsPort))
        }

        "https.proxyHost and https.proxyPort will be set if not previously set" in {
          val httpsProxyUri = "https://localhost:3128"
          val proxyEnvVariables = ProxyEnvVariables(None, Some(httpsProxyUri), None)
          HttpProxySupport.initProxyConfig(proxyEnvVariables, _ => ())

          assertResult("localhost")(System.getProperty(HttpsHost))
          assertResult("3128")(System.getProperty(HttpsPort))
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

        "translates property values" - {

          "single explicit domain" in {
            assertResult("something") {
              HttpProxySupport.translateNoProxy("something")
            }
          }

          "should allow excluded hosts to be separated by ','" in {
            assertResult("127.0.0.1|localhost") {
              HttpProxySupport.translateNoProxy("127.0.0.1,localhost")
            }
          }

          "should allow excluded hosts to be separated by '|'" in {
            assertResult("127.0.0.1|localhost") {
              HttpProxySupport.translateNoProxy("127.0.0.1|localhost")
            }
          }

          "should add wildcard in front of '.'-delimited domain suffixes" in {
            assertResult("*.mesos|*.example.com") {
              HttpProxySupport.translateNoProxy(".mesos,.example.com")
            }
          }

        }

        "respects http.nonProxyHosts" in {
          System.setProperty(NonHosts, "*.google.com")

          val proxyEnvVariables = ProxyEnvVariables(None, None, noProxy = Some("something"))
          HttpProxySupport.initProxyConfig(proxyEnvVariables, _ => ())

          assertResult("*.google.com")(System.getProperty(NonHosts))
        }

        "sets http.nonProxyHosts from env variable" in {
          val proxyEnvVariables = ProxyEnvVariables(None, None, noProxy = Some("something"))
          HttpProxySupport.initProxyConfig(proxyEnvVariables, _ => ())

          assertResult("something")(System.getProperty(NonHosts))
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

      }

      "Proxy Credentials" - {

        val vars = ProxyEnvVariables(
          httpProxyUri = Some("http://localhost:3128"),
          httpsProxyUri = Some("http://hostlocal:8213"),
          noProxy = Some("no_proxy_vals")
        )

        val authVars = ProxyEnvVariables(
          httpProxyUri = Some("http://test:testtest@localhost:3128"),
          httpsProxyUri = Some("http://foo:bar@hostlocal:8213"),
          noProxy = Some("no_proxy_vals")
        )

        "can be set from http_proxy, https_proxy and no_proxy" in {
          var authenticatorSet = false
          val setAuthenticator = (a: Authenticator) => { authenticatorSet = true }

          HttpProxySupport.initProxyConfig(vars, setAuthenticator)

          assertResult("localhost")(System.getProperty(HttpHost))
          assertResult("3128")(System.getProperty(HttpPort))
          assertResult("hostlocal")(System.getProperty(HttpsHost))
          assertResult("8213")(System.getProperty(HttpsPort))
          assertResult("no_proxy_vals")(System.getProperty(NonHosts))
          assert(authenticatorSet)
        }

        "are used in Authenticator (http)" in {
          val auth = new CosmosHttpProxyPasswordAuthenticator(authVars) {
            override def getRequestorType: RequestorType = RequestorType.PROXY
            override def getRequestingURL: URL = URI.create("http://someplace:123").toURL
          }

          val Some(authentication) = Option(auth.getPasswordAuthentication)

          assertResult("test")(authentication.getUserName)
          assertResult("testtest".toCharArray)(authentication.getPassword)
        }

        "are used in Authenticator (https)" in {
          val auth = new CosmosHttpProxyPasswordAuthenticator(authVars) {
            override def getRequestorType: RequestorType = RequestorType.PROXY
            override def getRequestingURL: URL = URI.create("https://someplace:123").toURL
          }

          val Some(authentication) = Option(auth.getPasswordAuthentication)

          assertResult("foo")(authentication.getUserName)
          assertResult("bar".toCharArray)(authentication.getPassword)
        }

        "are not provided when non http or https" in {
          val auth = new CosmosHttpProxyPasswordAuthenticator(authVars) {
            override def getRequestorType: RequestorType = RequestorType.PROXY
            override def getRequestingURL: URL = URI.create("ftp://someplace:123").toURL
          }

          val authentication = Option(auth.getPasswordAuthentication)
          assert(authentication.isEmpty)
        }

        "are not provided when requestorType is not proxy" in {
          val auth = new CosmosHttpProxyPasswordAuthenticator(authVars) {
            override def getRequestorType: RequestorType = RequestorType.SERVER
          }

          val authentication = Option(auth.getPasswordAuthentication)
          assert(authentication.isEmpty)
        }

        "are not provided when no user:pass in url" in {
          val auth = new CosmosHttpProxyPasswordAuthenticator(vars) {
            override def getRequestorType: RequestorType = RequestorType.PROXY
            override def getRequestingURL: URL = URI.create("https://someplace:123").toURL
          }

          val authentication = Option(auth.getPasswordAuthentication)
          assert(authentication.isEmpty)
        }

        "are not provided when no password in url" in {
          val vars = ProxyEnvVariables(
            httpProxyUri = Some("http://bill@localhost:3128"),
            httpsProxyUri = Some("http://alice@hostlocal:8213"),
            noProxy = Some("no_proxy_vals")
          )

          val auth = new CosmosHttpProxyPasswordAuthenticator(vars) {
            override def getRequestorType: RequestorType = RequestorType.PROXY
            override def getRequestingURL: URL = URI.create("https://someplace:123").toURL
          }

          val authentication = Option(auth.getPasswordAuthentication)
          assert(authentication.isEmpty)
        }

      }

    }

  }

}

object HttpProxySupportSpec {

  val HttpHost: String = "http.proxyHost"
  val HttpPort: String = "http.proxyPort"
  val HttpsHost: String = "https.proxyHost"
  val HttpsPort: String = "https.proxyPort"
  val NonHosts: String = "http.nonProxyHosts"

}
