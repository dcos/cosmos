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

    "be able to download a rewritten uri " - {
      for (packageName <- standardPackages) {
        val describeRequest = CosmosRequests.packageDescribeV3(DescribeRequest(packageName, None))
        val Right(describeResponse) = decode[DescribeResponse](CosmosClient.submit(describeRequest).contentString)
        describeResponse.`package`.images.map { images =>
          s"for images of $packageName" in {
            images.iconSmall.map(assertURLDownload)
            images.iconMedium.map(assertURLDownload)
            images.iconLarge.map(assertURLDownload)
            val screens = images.screenshots
            if(screens.isDefined && !screens.isEmpty) assertURLDownload(screens.get.head)
          }
        }

        s"for assets of $packageName" in {
          describeResponse.`package`.assets.map(
            _.uris
              .map(_.values)
              .map(_.head)
              .map(assertURLDownload)
          )
        }
      }
    }
  }

  private def assertURLDownload(url : String) : Assertion = {
    val future = HttpClient.fetch(
      Uri.parse(url),
      stats
    ) { responseData =>
      val buffer = Stream.continually(responseData.contentStream.read).takeWhile(_ != -1).map(_.toByte).toArray
      (buffer, responseData.contentLength)
    }
    val Right(result) = Await.result(future)
    val (buffer, optContentLength) = result
    if(optContentLength.isDefined) assert(optContentLength.get.toInt == buffer.length)
    assert(buffer.length > 0)
  }

}

object ResourceProxyHandlerIntegrationSpec {
  val stats: StatsReceiver = NullStatsReceiver
  val standardPackages = List("arangodb", "hello-world")
  val thirdPartyUnknownResource = Uri.parse("https://www.google.com/")
}
