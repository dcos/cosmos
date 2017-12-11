package com.mesosphere.cosmos.handler

import com.google.common.io.ByteStreams
import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.http.TestContext
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Fields
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Await
import io.circe.jawn.decode
import io.netty.handler.codec.http.HttpResponseStatus
import org.scalatest.Matchers
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.prop.TableFor1

final class ResourceProxyHandlerIntegrationSpec extends FreeSpec
  with TableDrivenPropertyChecks
  with Matchers {
  import ResourceProxyHandlerIntegrationSpec._

  "The ResourceProxyHandler should" - {

    "fail when the url is not in package collections" in {
      val response = intercept[CosmosException](CosmosClient.submit(
        CosmosRequests.packageResource(thirdPartyUnknownResource)
      ))
      assertResult(HttpResponseStatus.FORBIDDEN)(response.error.status)
    }

    "be able to download rewritten uris for images and assets" in {
      forAll(standardPackages) { packageName =>
        val Right(describeResponse) = decode[rpc.v3.model.DescribeResponse](
          CosmosClient.submit(
            CosmosRequests.packageDescribeV3(
              rpc.v1.model.DescribeRequest(packageName, None)
            )
          ).contentString
        )

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
      Fields.Authorization -> testContext.token.get.headerValue
    ) { responseData =>
      val contentLength = responseData.contentLength
      contentLength shouldBe defined
      val buffer = Array.ofDim[Byte](contentLength.get.toInt)
      val numberOfBytesRead = ByteStreams.read(responseData.contentStream, buffer, 0, buffer.length)
      val lastRead = ByteStreams.read(responseData.contentStream, Array.ofDim(1), 0, 1)
      (contentLength.get, numberOfBytesRead, lastRead)
    }

    val (contentLength, numberOfBytesRead, lastRead) = Await.result(future)

    contentLength.toInt should be > 0
    contentLength shouldEqual numberOfBytesRead
    lastRead shouldEqual 0
  }
}

object ResourceProxyHandlerIntegrationSpec {
  lazy val testContext = TestContext.fromSystemProperties()
  implicit val stats: StatsReceiver = NullStatsReceiver
  val standardPackages = new TableFor1(
    "packageName",
    "arangodb",
    "hello-world"
  )
  val thirdPartyUnknownResource = Uri.parse("https://www.google.com/")
}
