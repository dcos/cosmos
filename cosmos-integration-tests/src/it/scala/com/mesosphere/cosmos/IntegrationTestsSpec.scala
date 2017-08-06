package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.TestContext
import com.netaporter.uri.dsl._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suites

final class IntegrationTestsSpec extends Suites(
  new ListVersionsSpec,
  new NonSharedServiceDescribeSpec,
  new PackageDescribeSpec,
  new PackageInstallIntegrationSpec,
  new PackageListIntegrationSpec,
  new PackageRepositorySpec,
  new PackageSearchSpec,
  new ServiceDescribeSpec,
  new handler.CapabilitiesHandlerSpec,
  new handler.PackageRenderHandlerSpec,
  new handler.RequestErrorsSpec,
  new handler.UninstallHandlerSpec,
  new rpc.v1.model.ErrorResponseSpec
) with BeforeAndAfterAll {

  private[this] implicit val testContext = TestContext.fromSystemProperties()

  private[this] val universeUri = "https://downloads.mesosphere.com/universe/02493e40f8564a39446d06c002f8dcc8e7f6d61f/repo-up-to-1.8.json"

  override def beforeAll(): Unit = {
    Requests.deleteRepository(Some("Universe"))

    Requests.addRepository(
      "Universe",
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
    Requests.deleteRepository(None, Some(universeUri))
    val _ = Requests.addRepository("Universe", "https://universe.mesosphere.com/repo")
  }
}
