package com.mesosphere.cosmos.http

import com.mesosphere.http.OriginHostScheme
import io.lemonlabs.uri.Uri
import org.slf4j.LoggerFactory

final case class TestContext(
  uri: Uri,
  token: Option[Authorization],
  originInfo: OriginHostScheme)

object TestContext {

  private[this] lazy val logger = LoggerFactory.getLogger(getClass)

  def fromSystemProperties(): TestContext = {
    val url = Uri.parse(getClientProperty("CosmosClient", "uri"))
    val token = sys.env.get("COSMOS_AUTHORIZATION_HEADER").map { token =>
      val maxDisplayWidth = 10
      val tokenDisplay = token.stripPrefix("token=").take(maxDisplayWidth)
      logger.info(s"Loaded authorization token '$tokenDisplay...' from environment")
      Authorization(token)
    }

    TestContext(
      url,
      token,
      OriginHostScheme(
        extractHostFromUri(url),
        OriginHostScheme.Scheme(url.schemeOption.get).get
      )
    )
  }

  private def getClientProperty(clientName: String, key: String): String = {
    val property = s"com.mesosphere.cosmos.test.CosmosIntegrationTestClient.$clientName.$key"
    Option(System.getProperty(property))
      .getOrElse(throw new AssertionError(s"Missing system property '$property' "))
  }

  def extractHostFromUri(uri: Uri): String = s"${uri.toUrl.hostOption.get}${
    uri.toUrl.port match {
      case Some(x) => s":$x"
      case None => ""
    }
  }"
}
