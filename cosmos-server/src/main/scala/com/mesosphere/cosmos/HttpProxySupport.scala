package com.mesosphere.cosmos

import io.lemonlabs.uri.Uri
import java.net.Authenticator.RequestorType
import java.net.Authenticator
import java.net.PasswordAuthentication

private[cosmos] object HttpProxySupport {

  private[this] val HttpProxyHost = "http.proxyHost"
  private[this] val HttpProxyPort = "http.proxyPort"
  private[this] val HttpsProxyHost = "https.proxyHost"
  private[this] val HttpsProxyPort = "https.proxyPort"
  private[this] val HttpProxyNoHosts = "http.nonProxyHosts"

  private[cosmos] case class ProxyEnvVariables(httpProxyUri: Option[Uri], httpsProxyUri: Option[Uri], noProxy: Option[String])

  /**
    * Goes through the process of setting up the JVM to allow HTTP(s) requests to go through a proxy
    * <p>
    * Priority of where proxy information is resolved:
    * <ul>
    *   <li>
    *     HTTP Proxy Host
    *     <ol>
    *       <li>Java system property `http.proxyHost`</li>
    *       <li>Environment Variable `http_proxy`</li>
    *       <li>Environment Variable `HTTP_PROXY`</li>
    *     </ol>
    *   </li>
    *   <li>
    *     HTTP Proxy Port
    *     <ol>
    *       <li>Java system property `http.proxyPort`</li>
    *       <li>Environment Variable `http_proxy`</li>
    *       <li>Environment Variable `HTTP_PROXY`</li>
    *     </ol>
    *     If either `http_proxy` or `HTTP_PROXY` are used and a port is not part of the URI, the standard default ports
    *     will be used as a fallback. Namely http will be port 80 https will be port 443
    *   </li>
    *   <li>
    *     HTTP User name and password
    *     <ol>
    *       <li>Environment Variable `http_proxy`</li>
    *       <li>Environment Variable `HTTP_PROXY`</li>
    *     </ol>
    *   </li>
    *
    *   <li>
    *     HTTPS Proxy Host
    *     <ol>
    *       <li>Java system property `https.proxyHost`</li>
    *       <li>Environment Variable `https_proxy`</li>
    *       <li>Environment Variable `HTTPS_PROXY`</li>
    *     </ol>
    *   </li>
    *   <li>
    *     HTTPS Proxy Port
    *     <ol>
    *       <li>Java system property `https.proxyPort`</li>
    *       <li>Environment Variable `https_proxy`</li>
    *       <li>Environment Variable `HTTPS_PROXY`</li>
    *     </ol>
    *     If either `http_proxy` or `HTTP_PROXY` are used and a port is not part of the URI, the standard default ports
    *     will be used as a fallback. Namely http will be port 80 https will be port 443
    *   </li>
    *   <li>
    *     HTTPS User name and password
    *     <ol>
    *       <li>Environment Variable `https_proxy`</li>
    *       <li>Environment Variable `HTTPS_PROXY`</li>
    *     </ol>
    *   </li>
    *   <li>
    *     Hosts to exclude from proxying (HTTP and HTTPS)
    *     <ol>
    *       <li>Java system property `http.nonProxyHosts`</li>
    *       <li>Environment Variable `no_proxy`</li>
    *     </ol>
    *   </li>
    * </ul>
    *
    * <p>
    *
    * The format of environment variables is expected to be in the form of a URI:
    * `http[s]://[user:pass@]host[:port]`
    *
    * @see [[https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html#Proxies]]
    */
  def configureProxySupport(): Unit = {
    val proxyEnvVariables = extractProxyEnvVariables(sys.env)
    initProxyConfig(proxyEnvVariables, Authenticator.setDefault)
  }

  private[cosmos] def extractProxyEnvVariables(env: Map[String, String]): ProxyEnvVariables = {
    val e = envVar(env) _
    val httpProxy = e("http_proxy")
    val httpsProxy = e("https_proxy")
    val noProxy = e("no_proxy")
    val httpProxyUri = httpProxy.map(Uri.parse)
    val httpsProxyUri = httpsProxy.map(Uri.parse)
    ProxyEnvVariables(httpProxyUri, httpsProxyUri, noProxy)
  }

  private[cosmos] def initProxyConfig(
    proxyEnvVariables: ProxyEnvVariables,
    setAuthenticator: Authenticator => Unit
  ): Unit = {
    parseAllProperties(proxyEnvVariables)
      .foreach { case (key, value) => setPropertyIfUnset(key, value) }

    // Ensure we can use Basic authentication for HTTPS tunneling through proxies
    setPropertyIfUnset("jdk.http.auth.tunneling.disabledSchemes", "")

    val authenticator = new CosmosHttpProxyPasswordAuthenticator(proxyEnvVariables)
    setAuthenticator(authenticator)
  }

  private[this] def parseAllProperties(
    proxyEnvVariables: ProxyEnvVariables
  ): List[(String, String)] = {
    val ProxyEnvVariables(httpProxyUri, httpsProxyUri, noProxy) = proxyEnvVariables
    def parseWith[A, B](v: Option[A])(f: A => List[B]): List[B] = v.toList.flatMap(f)

    parseWith(httpProxyUri)(parseHostPortProperties(HttpProxyHost, HttpProxyPort)) ++
      parseWith(httpsProxyUri)(parseHostPortProperties(HttpsProxyHost, HttpsProxyPort)) ++
      noProxy.map(np => (HttpProxyNoHosts, translateNoProxy(np)))
  }

  def parseHostPortProperties(hostProperty: String, portProperty: String)(
    proxyUri: Uri
  ): List[(String, String)] = {
    Uris.extractHostAndPort(proxyUri)
      .toOption
      .toList
      .flatMap { case ConnectionDetails(host, port, _) =>
        List((hostProperty, host), (portProperty, port.toString))
      }
  }

  /**
   * Normalizes the input into the format required by the `http.nonProxyHosts` system property.
   * @see https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html#Proxies
   */
  def translateNoProxy(noProxy: String): String = {
    noProxy
      .split(Array(',', '|'))
      .map(suffix => if (suffix.startsWith(".")) "*" + suffix else suffix)
      .mkString("|")
  }

  private[this] def setPropertyIfUnset(key: String, value: String): Unit = {
    if (Option(System.getProperty(key)).isEmpty) {
      val _ = System.setProperty(key, value)
    }
  }

  private[this] def envVar(env: Map[String, String])(name: String): Option[String] = {
    env.get(name.toLowerCase)
      .orElse(env.get(name.toUpperCase))
  }

  private[cosmos] class CosmosHttpProxyPasswordAuthenticator(proxyEnvVariables: ProxyEnvVariables) extends Authenticator {
    override def getPasswordAuthentication: PasswordAuthentication = {
      if (getRequestorType == RequestorType.PROXY) {
        val protocol = getRequestingURL.getProtocol
        val uriOpt = protocol match {
          case "https" =>
            proxyEnvVariables.httpsProxyUri
          case "http" =>
            proxyEnvVariables.httpProxyUri
          case _ => None
        }

        val passAuth = uriOpt.map(_.toUrl).map(url => (url.user, url.password)).flatMap {
          case (Some(user), Some(pass)) =>
            Some(new PasswordAuthentication(user, pass.toCharArray))
          case _ => None
        }

        passAuth.getOrElse(super.getPasswordAuthentication)
      } else {
        super.getPasswordAuthentication
      }
    }
  }
}
