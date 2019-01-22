package com.mesosphere.cosmos

import io.lemonlabs.uri.Uri
import com.twitter.util.Try

object Uris {

  private[this] val httpsPort = 443
  private[this] val httpPort = 80

  def extractHostAndPort(uri: Uri): Try[ConnectionDetails] = Try {
    val url = uri.toUrl
    (url.schemeOption, url.hostOption, url.port) match {
      case (Some("https"), Some(h), p) => ConnectionDetails(h.toString, p.getOrElse(httpsPort), tls = true)
      case (Some("http"), Some(h), p) => ConnectionDetails(h.toString, p.getOrElse(httpPort))
      case (_, _, _) => throw err(url.toString)
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

}
