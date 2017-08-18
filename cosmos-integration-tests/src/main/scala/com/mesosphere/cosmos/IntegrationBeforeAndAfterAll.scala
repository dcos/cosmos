package com.mesosphere.cosmos

import com.netaporter.uri.dsl._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite

trait IntegrationBeforeAndAfterAll extends BeforeAndAfterAll { this: Suite =>

  private[this] val universeUri = "https://downloads.mesosphere.com/universe/02493e40f8564a39446d06c002f8dcc8e7f6d61f/repo-up-to-1.8.json"
  private[this] val universeConverterUri = "https://universe-converter.mesosphere.com/transform?url=" + universeUri

  override def beforeAll(): Unit = {
    Requests.deleteRepository(Some("Universe"))

    Requests.addRepository(
      "Universe",
      universeConverterUri,
      Some(0)
    )

    val _ = Requests.addRepository(
      "V4TestUniverse",
      ItObjects.V4TestUniverseConverterURI,
      Some(0)
    )
  }

  override def afterAll(): Unit = {
    Requests.deleteRepository(Some("V4TestUniverse"))
    Requests.deleteRepository(None, Some(universeConverterUri))
    val _ = Requests.addRepository("Universe", "https://universe.mesosphere.com/repo")
  }
}
