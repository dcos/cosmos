package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.Requests
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.rpc.v1.model.DescribeRequest
import com.mesosphere.cosmos.rpc.v3.model.DescribeResponse
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Await
import io.circe.jawn.decode
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import org.scalatest.prop.TableDrivenPropertyChecks

final class ResourceProxyHandlerIntegrationSpec extends FreeSpec with TableDrivenPropertyChecks {

  import ResourceProxyHandlerIntegrationSpec._

  "The ResourceProxyHandler should" - {

    "fail when the url is not in package collections" in {
      val response = intercept[CosmosException](Requests.packageResource(thirdPartyUnknownResource))
      assertResult(Status.Forbidden)(response.status)
    }

    for (packageName <- standardPackages) {
      s"be able to download rewritten uris for $packageName images and assets" in {
        val describeRequest = CosmosRequests.packageDescribeV3(DescribeRequest(packageName, None))
        val content = CosmosClient.submit(describeRequest).contentString
        val Right(describeResponse) = decode[DescribeResponse](content)

        describeResponse.`package`.images.map { images =>
          images.iconSmall.map(assertURLDownload)
          images.iconMedium.map(assertURLDownload)
          images.iconLarge.map(assertURLDownload)
          val screens = images.screenshots
          if(screens.isDefined && !screens.isEmpty) {
            assertURLDownload(screens.get.head)
          }
        }

        describeResponse.`package`.assets.map(
          _.uris.map(x => assertURLDownload(x.values.head))
        )
      }
    }
  }

  private def assertURLDownload(url : String) : Assertion = {
    val future = HttpClient.fetch(
      Uri.parse(url),
      stats
    ) { responseData =>
      responseData.contentStream.read()
    }
    val firstByte = Await.result(future).toTry.get
    assert(firstByte != -1) // will fail if EOF is reached at the beginning of stream
  }

}

object ResourceProxyHandlerIntegrationSpec {
  val stats: StatsReceiver = NullStatsReceiver
  val standardPackages = List("arangodb", "hello-world")
  val thirdPartyUnknownResource = Uri.parse("https://www.google.com/")
}
