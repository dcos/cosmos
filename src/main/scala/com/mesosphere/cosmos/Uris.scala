package com.mesosphere.cosmos

import com.netaporter.uri.Uri

object Uris {

  def stripTrailingSlash(uri: Uri): String = {
    val uriString = uri.toString
    if (uriString.endsWith("/")) {
      uriString.substring(0, uriString.length - 1)
    } else {
      uriString
    }
  }

}
