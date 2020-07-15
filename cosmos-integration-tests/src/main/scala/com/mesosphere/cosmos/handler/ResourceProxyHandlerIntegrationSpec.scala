package com.mesosphere.cosmos.handler

import com.google.common.io.ByteStreams
import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.IntegrationBeforeAndAfterAll
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.http.TestContext
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import io.lemonlabs.uri.Uri
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
  with Matchers
  with IntegrationBeforeAndAfterAll {
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
          if(screens.isDefined) {
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
    val parsedUrl = Uri.parse(url)
    val future = HttpClient.fetch(
      parsedUrl,
      Fields.Authorization -> testContext.token.headerValue
    ) { responseData =>
      val contentLength = responseData.contentLength
      // Resource Proxy endpoint response is chunked.
      contentLength shouldBe empty
      val contentBytes = ByteStreams.toByteArray(responseData.contentStream)
      contentBytes.length
    }
    val numberOfBytesRead = Await.result(future)
    numberOfBytesRead should be > 0
    val rawUrl = parsedUrl.toUrl
      .query
      .params
      .find(x => x._1.equals("url"))
      .flatMap(_._2)
    rawUrl shouldBe defined
    val rawContentBytesLength = rawUrl.flatMap { rawUrl =>
      Await.result { HttpClient.fetch(Uri.parse(rawUrl))(_.contentLength) }
    }
    // All the resource urls in the universe repo should define a Content-Length header.
    rawContentBytesLength shouldBe defined
    rawContentBytesLength.get.toInt shouldEqual numberOfBytesRead
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
