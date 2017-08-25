package com.mesosphere.cosmos.handler

import com.mesosphere.Generators.Implicits._
import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.HttpClient.ResponseData
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.ResourceTooLarge
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.ResourceProxyData
import com.netaporter.uri.Uri
import com.twitter.conversions.storage._
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.StorageUnit
import io.finch.Output
import java.io.ByteArrayInputStream
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks
import scala.collection.mutable

final class ResourceProxyHandlerSpec extends FreeSpec with PropertyChecks {

  "When Content-Length is provided by the upstream server" - {

    "Succeeds if Content-Length is below the limit" in {
      val resourceData = ResourceProxyData.IconSmall
      val lengthLimit = resourceData.contentLength + 1.bytes
      val proxyHandler = ResourceProxyHandler(HttpClient, lengthLimit, NullStatsReceiver)
      val output = Await.result(proxyHandler(resourceData.uri))

      assertResult(Status.Ok)(output.status)
    }

    "Fails if Content-Length is at the limit" in {
      val resourceData = ResourceProxyData.IconSmall
      val lengthLimit = resourceData.contentLength
      val proxyHandler = ResourceProxyHandler(HttpClient, lengthLimit, NullStatsReceiver)

      assertFailure(proxyHandler(resourceData.uri))
    }

    "Fails if Content-Length is above the limit" in {
      val resourceData = ResourceProxyData.IconSmall
      val lengthLimit = resourceData.contentLength - 1.bytes
      val proxyHandler = ResourceProxyHandler(HttpClient, lengthLimit, NullStatsReceiver)

      assertFailure(proxyHandler(resourceData.uri))
    }

  }

  "When Content-Length is not provided by the upstream server" - {

    "Fails if the content stream length is >= the limit" in {
      type TestData = (StorageUnit, mutable.WrappedArray[Byte], Uri, MediaType)
      val maxLengthLimit = 100
      val maxExcessLength = 20
      val genTestData: Gen[TestData] = for {
        lengthLimit <- Gen.chooseNum(1, maxLengthLimit)
        excessLength <- Gen.chooseNum(0, maxExcessLength)
        contentBytes <- Gen.containerOfN[Array, Byte](lengthLimit + excessLength, arbitrary[Byte])
        uri <- arbitrary[Uri]
        contentType <- arbitrary[MediaType]
      } yield {
        (lengthLimit.bytes, contentBytes, uri, contentType)
      }

      forAll (genTestData) { case (lengthLimit, contentBytes, uri, contentType) =>
        whenever(contentBytes.length >= lengthLimit.bytes) {
          val contentStream = new ByteArrayInputStream(contentBytes.array)
          val responseData = ResponseData(contentType, None, contentStream)
          val httpClient = new ConstantResponseClient(responseData)
          val proxyHandler = ResourceProxyHandler(httpClient, lengthLimit, NullStatsReceiver)

          assertFailure(proxyHandler(uri))
        }
      }
    }

    // TODO proxy Test cases
    // If ContentLength was not specified, and the stream is < the limit, pass

  }

  private[this] def assertFailure(output: Future[Output[Response]]): Assertion = {
    val exception = intercept[CosmosException](Await.result(output))

    assertResult(Status.Forbidden)(exception.status)
    assert(exception.error.isInstanceOf[ResourceTooLarge])
  }

  // TODO proxy Test cases
  // If ContentLength *was* specified, and the stream is > that, fail
  // If ContentLength *was* specified, and the stream is == that, pass
  // If ContentLength *was* specified, and the stream is < that, pass (don't need to test this)
  //   - Verify that our ContentLength is the actual size of the data

}

final class ConstantResponseClient(responseData: ResponseData) extends HttpClient {

  override def fetch[A](
    uri: Uri,
    statsReceiver: StatsReceiver,
    headers: (String, String)*
  )(
    processResponse: ResponseData => A
  ): Future[Either[HttpClient.Error, A]] = {
    Future(Right(processResponse(responseData)))
  }

}
