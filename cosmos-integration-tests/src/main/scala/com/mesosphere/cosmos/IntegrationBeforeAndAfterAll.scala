package com.mesosphere.cosmos

import com.google.common.io.CharStreams
import com.mesosphere.cosmos.circe.Decoders.parse
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.Status
import io.circe.jawn.decode
import java.io.InputStreamReader
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import org.scalatest.concurrent.Eventually
import scala.concurrent.duration._

trait IntegrationBeforeAndAfterAll extends BeforeAndAfterAll with Eventually { this: Suite =>

  private[this] val universeUri = "https://downloads.mesosphere.com/universe/02493e40f8564a39446d06c002f8dcc8e7f6d61f/repo-up-to-1.8.json"
  private[this] val universeConverterUri = "https://universe-converter.mesosphere.com/transform?url=" + universeUri

  // TODO: Move this to downloads.mesosphere.com
  val v5TestPackage = "https://infinity-artifacts.s3.amazonaws.com/cosmos-integration-test/package-with-manager/stub-universe-hello-world.json"

  override def beforeAll(): Unit = {
    Requests.deleteRepository(Some("Universe"))

    val customPkgMgrResource = s"/${ItObjects.customManagerAppName}.json"

    Requests
      .postMarathonApp(
        parse(
          Option(this.getClass.getResourceAsStream(customPkgMgrResource)) match {
            case Some(is) =>
              CharStreams.toString(new InputStreamReader(is))
            case _ =>
              throw new IllegalStateException(s"Unable to load classpath resource: $customPkgMgrResource")
          }
        ).toOption.get.asObject.get
      )
    Requests.waitForDeployments()

    // TODO: add a healthcheck to test marathon app and remove this
    Thread.sleep(10000) //scalastyle:ignore magic.number

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
    Requests.deleteRepository(Some("V5TestPackage"))
    Requests.deleteMarathonApp(AppId(ItObjects.customManagerAppName))
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
