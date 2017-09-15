package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.Requests
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.http.ResourceProxyData
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
    "be able to proxy a package icon file" in {
      assertSuccess(ResourceProxyData.IconSmall)
    }

    "be able to proxy a subcommand binary" in {
      assertSuccess(ResourceProxyData.LinuxBinary)
    }

    "fail when the url is not in package collections" in {
      val response = intercept[CosmosException](Requests.packageResource(ResourceProxyData.thirdPartyUnknownResource.uri))
      assertResult(Status.Forbidden)(response.status)
    }

    "be able to download a rewritten uri" in {
      for (packageName <- List("cassandra", "arangodb")) {
        val describeRequest = CosmosRequests.packageDescribeV3(DescribeRequest(packageName, None))
        val Right(describeResponse) = decode[DescribeResponse](CosmosClient.submit(describeRequest).contentString)
        describeResponse.`package`.images.map { images =>
          images.iconSmall.map(assertURLDownload)
          val screens = images.screenshots
          if(screens.isDefined && !screens.isEmpty) assertURLDownload(screens.get.head)
        }
        describeResponse.`package`.assets.map(
          _.uris
            .map(_.values)
            .map(_.head)
            .map(assertURLDownload)
        )
      }
    }
  }

  private def assertURLDownload(url : String) : Assertion = {
    val future = HttpClient.fetch(Uri.parse(url), stats) { responseData =>
      responseData.contentStream
    }
    val Right(result) = Await.result(future)
    assert(result.available() > 0)
  }

  private def assertSuccess(data: ResourceProxyData): Assertion = {
    val response = Requests.packageResource(data.uri)
    assertResult(Status.Ok)(response.status)
    assertResult(Some(data.contentType))(response.contentType)
    assertResult(Some(data.contentLength.bytes))(response.contentLength)
  }

}

object ResourceProxyHandlerIntegrationSpec {
  val stats: StatsReceiver = NullStatsReceiver
}
