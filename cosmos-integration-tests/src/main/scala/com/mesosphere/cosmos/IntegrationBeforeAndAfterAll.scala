package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.Status
import io.circe.jawn.decode
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import org.scalatest.concurrent.Eventually
import scala.concurrent.duration._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._


trait IntegrationBeforeAndAfterAll extends BeforeAndAfterAll with Eventually { this: Suite =>

  private[this] val universeUri = "https://downloads.mesosphere.com/universe/02493e40f8564a39446d06c002f8dcc8e7f6d61f/repo-up-to-1.8.json"
  private[this] val universeConverterUri = "https://universe-converter.mesosphere.com/transform?url=" + universeUri

  override def beforeAll(): Unit = {
    Requests.deleteRepository(Some("Universe"))

    Requests.addRepository(
      "Universe",
      universeConverterUri,
      Some(0)
    )

    Requests.addRepository(
      "V4TestUniverse",
      ItObjects.V4TestUniverseConverterURI,
      Some(0)
    )

    // This package is present only in V4TestUniverse and this method ensures that the
    // package collection cache is cleared before starting the integration tests
    val _ = waitUntilCacheReloads()
  }

  override def afterAll(): Unit = {
    Requests.deleteRepository(Some("V4TestUniverse"))
    Requests.deleteRepository(None, Some(universeConverterUri))
    val _ = Requests.addRepository("Universe", "https://universe.mesosphere.com/repo")
  }

  private[this] def waitUntilCacheReloads(): Assertion = {
    val packageName = "helloworld-invalid"
    eventually(timeout(2.minutes), interval(10.seconds)) {
      val response = CosmosClient.submit(
        CosmosRequests.packageDescribeV3(rpc.v1.model.DescribeRequest(packageName, None))
      )
      assertResult(Status.Ok)(response.status)
      val Right(actualResponse) = decode[rpc.v3.model.DescribeResponse](response.contentString)
      assert(actualResponse.`package`.name == packageName)
    }
  }
}
