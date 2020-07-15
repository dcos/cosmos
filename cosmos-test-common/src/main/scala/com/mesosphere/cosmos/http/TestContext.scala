package com.mesosphere.cosmos.http

import com.mesosphere.http.OriginHostScheme
import io.lemonlabs.uri.Uri
import sys.process._

final case class TestContext(
  direct: Boolean,
  uri: Uri,
  token: Authorization,
  originInfo: OriginHostScheme)

object TestContext {
  def fromSystemProperties(): TestContext = {
    val url = Uri.parse(System.getProperty("com.mesosphere.cosmos.dcosUri")).toUrl
    val token = Authorization(sys.env.getOrElse(
      "COSMOS_AUTHORIZATION_HEADER",
      Seq("bash", "-c", s"CLUSTER_URL=${url} ./ci/auth_token.sh").!!.stripLineEnd)
    )

    TestContext(
      false,
      url,
      token,
      OriginHostScheme(
        extractHostFromUri(url),
        OriginHostScheme.Scheme(url.schemeOption.get).get
      )
    )
  }

  def extractHostFromUri(uri: Uri): String = s"${uri.toUrl.hostOption.get}${
    uri.toUrl.port match {
      case Some(x) => s":$x"
      case None => ""
    }
  }"
}
