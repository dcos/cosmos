package com.mesosphere.cosmos.http

final case class TestContext(direct: Boolean)

object TestContext {
  def fromSystemProperties(): TestContext = {
    val property = "com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient.direct"

    TestContext(
      Option(System.getProperty(property)).map(_.toBoolean).get
    )
  }
}
