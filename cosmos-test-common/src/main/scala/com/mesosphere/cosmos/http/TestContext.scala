package com.mesosphere.cosmos.http

import com.mesosphere.http.OriginHostScheme
import io.lemonlabs.uri.{Uri, Url}
import scala.sys.process._

final case class TestContext(
  uri: Uri,
  token: Option[Authorization],
  originInfo: OriginHostScheme)

object TestContext {
  def fromSystemProperties(): TestContext = {
    val url = Url.parse(System.getProperty("com.mesosphere.cosmos.dcosUri"))
    val token = Some(Authorization(Seq("bash", "-c", s"CLUSTER_URL=${url} ./ci/auth_token.sh").!!.stripLineEnd))

    TestContext(
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
