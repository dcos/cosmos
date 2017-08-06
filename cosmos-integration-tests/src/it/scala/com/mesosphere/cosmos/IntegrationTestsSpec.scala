package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.TestContext
import com.netaporter.uri.dsl._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suites

final class IntegrationTestSpec extends Suites(
  new ListVersionsSpec,
  new PackageDescribeSpec,
  new PackageInstallIntegrationSpec,
  new PackageListIntegrationSpec,
  new PackageRepositorySpec,
  new PackageSearchSpec,
  new ServiceDescribeSpec
) with BeforeAndAfterAll {

  private[this] implicit val testContext = TestContext.fromSystemProperties()

  private[this] val universeUri = "https://downloads.mesosphere.com/universe/02493e40f8564a39446d06c002f8dcc8e7f6d61f/repo-up-to-1.8.json"

  override def beforeAll(): Unit = {
    Requests.addRepository(
      "OldUniverse",
      universeUri,
      Some(0)
    )

    val _ = Requests.addRepository(
      "V4TestUniverse",
      ItObjects.V4TestUniverse,
      Some(0)
    )
  }

  override def afterAll(): Unit = {
    Requests.deleteRepository(Some("V4TestUniverse"))
    val _ = Requests.deleteRepository(None, Some(universeUri))
  }
}
