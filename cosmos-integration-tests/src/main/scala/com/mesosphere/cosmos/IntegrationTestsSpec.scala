package com.mesosphere.cosmos

final class IntegrationTestSpec extends Suite(
) with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    val ignored = Requests.addRepository(
      "V4TestUniverse",
      "https://downloads.mesosphere.com/universe/ae6a07ac0b53924154add2cd61403c5233272d93/repo/repo-up-to-1.10.json",
      Some(0)
    )
  }

  override def afterAll(): Unit = {
    val ignored = Requests.deleteRepository(Some("V4TestUniverse"))
  }
}
