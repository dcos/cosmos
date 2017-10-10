package com.mesosphere.cosmos

import com.mesosphere.http.OriginHostScheme
import com.netaporter.uri.Uri
import scala.util.Failure
import scala.util.Success
import scala.util.Try

package object repository {
  def rewriteUrlWithProxyInfo(
    origin: OriginHostScheme
  )(
    value: String
  ): String = {
    Try(Uri.parse(value)) match {
      case Success(url) =>
        // TODO: This can throw!!
        Uri.parse(
          s"${origin.urlScheme}://${origin.rawHost}/package/resource?url=$url"
        ).toString
      case Failure(_) =>
        value
    }
  }
}
