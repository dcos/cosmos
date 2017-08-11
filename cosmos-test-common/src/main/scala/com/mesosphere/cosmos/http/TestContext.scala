package com.mesosphere.cosmos.http

final case class TestContext(direct: Boolean)

object TestContext {
  def fromSystemProperties(): TestContext = {
    val property = "com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient.direct"

    TestContext(
      // TODO: replace getOrElse with get
      Option(System.getProperty(property)).map(_.toBoolean).getOrElse(true)
    )
  }
}
