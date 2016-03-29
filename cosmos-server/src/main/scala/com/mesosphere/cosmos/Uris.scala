package com.mesosphere.cosmos

import com.netaporter.uri.Uri
import com.twitter.util.Try

object Uris {

  def extractHostAndPort(uri: Uri): Try[ConnectionDetails] = Try {
    (uri.scheme, uri.host, uri.port) match {
      case (Some("https"), Some(h), p) => ConnectionDetails(h, p.getOrElse(443), tls = true)
      case (Some("http"), Some(h), p) => ConnectionDetails(h, p.getOrElse(80), tls = false)
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

}
