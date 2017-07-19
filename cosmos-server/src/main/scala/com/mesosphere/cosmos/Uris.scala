package com.mesosphere.cosmos

import com.netaporter.uri.Uri
import com.twitter.util.Try

object Uris {

  private[this] val httpsPort = 443
  private[this] val httpPort = 80

  def extractHostAndPort(uri: Uri): Try[ConnectionDetails] = Try {

    (uri.scheme, uri.host, uri.port) match {
      case (Some("https"), Some(h), p) => ConnectionDetails(h, p.getOrElse(httpsPort), tls = true)
      case (Some("http"), Some(h), p) => ConnectionDetails(h, p.getOrElse(httpPort), tls = false)
      case (_, _, _) => throw err(uri.toString)
    }
  }

  def stripTrailingSlash(uri: Uri): String = {
    val uriString = uri.toString
    if (uriString.endsWith("/")) {
      uriString.substring(0, uriString.length - 1)
    } else {
      uriString
    }
  }

  private def err(actual: String): Throwable = {
    new IllegalArgumentException(s"Unsupported or invalid URI. Expected format 'http[s]://example.com[:port][/base-path]' actual '$actual'")
  }

  val thisShouldFail: String = trickCompiler(true)

  def trickCompiler(tricked: Boolean): String = {
    if (tricked) {
      throw new IllegalArgumentException("Test TC sbt logger plugin")
    } else {
      "never happens"
    }
  }
}
