package com.mesosphere.cosmos.handler

import com.mesosphere.Generators.Implicits._
import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.HttpClient.ResponseData
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.GenericHttpError
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

    "When Content-Length matches the actual content stream length" - {

      "Succeeds if Content-Length is below the limit" in {
        val resourceData = ResourceProxyData.IconSmall
        val lengthLimit = resourceData.contentLength + 1.bytes
        val proxyHandler = ResourceProxyHandler(HttpClient, lengthLimit, NullStatsReceiver)

        assertSuccess(proxyHandler(resourceData.uri))
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

    "When Content-Length does not match the actual content stream length" - {
      // TODO proxy Test cases
      // If ContentLength *was* specified, and the stream is > that, fail
      // If ContentLength *was* specified, and the stream is <= that, pass
      //   - In this case our response content length should be the size of the received data
      "If ContentLength was specified, and the stream is not equal to that, fail" in {
        val maxLengthLimit = 100
        val genTestData: Gen[TestData] = for {
          lengthLimit <- Gen.chooseNum(1, maxLengthLimit)
          contentBytes <- Gen.containerOfN[Array, Byte](lengthLimit, arbitrary[Byte])
          uri <- arbitrary[Uri]
          contentType <- arbitrary[MediaType]
        } yield {
          (lengthLimit.bytes, contentBytes, uri, contentType)
        }

        forAll (genTestData) { case (lengthLimit, contentBytes, uri, contentType) =>
          whenever(contentBytes.array.length > 1) {
            val contentStream = new ByteArrayInputStream(contentBytes.array)
            val responseData = ResponseData(contentType, Some((contentBytes.size - 1).toLong), contentStream)
            val ex = intercept[CosmosException](ResourceProxyHandler.getContentBytes(uri, responseData, lengthLimit))
            assert(ex.error.isInstanceOf[GenericHttpError])
            assertResult(Status.InternalServerError)(ex.error.asInstanceOf[GenericHttpError].clientStatus)
          }
        }
      }
    }

  }

  "When Content-Length is not provided by the upstream server" - {

    "Fails if the content stream length is >= the limit" in {
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
          val exception = intercept[CosmosException](ResourceProxyHandler.getContentBytes(uri, responseData, lengthLimit))
          assert(exception.error.isInstanceOf[ResourceTooLarge])
        }
      }
    }

    "Succeeds if the content stream length is < the limit" in {
      val maxLengthLimit = 100
      val genTestData: Gen[TestData] = for {
        lengthLimit <- Gen.chooseNum(1, maxLengthLimit)
        actualLength <- Gen.chooseNum(0, lengthLimit - 1)
        contentBytes <- Gen.containerOfN[Array, Byte](actualLength, arbitrary[Byte])
        uri <- arbitrary[Uri]
        contentType <- arbitrary[MediaType]
      } yield {
        (lengthLimit.bytes, contentBytes, uri, contentType)
      }

      forAll (genTestData) { case (lengthLimit, contentBytes, uri, contentType) =>
        whenever(contentBytes.length < lengthLimit.bytes) {
          val contentStream = new ByteArrayInputStream(contentBytes.array)
          val responseData = ResponseData(contentType, None, contentStream)
          val bytesRead = ResourceProxyHandler.getContentBytes(uri, responseData, lengthLimit)
          assertResult(contentBytes.array)(bytesRead)
        }
      }
    }

  }

  type TestData = (StorageUnit, mutable.WrappedArray[Byte], Uri, MediaType)

  private[this] def assertSuccess(output: Future[Output[Response]]): Assertion = {
    val result = Await.result(output)
    assertResult(Status.Ok)(result.status)
  }

  private[this] def assertFailure(output: Future[Output[Response]]): Assertion = {
    val exception = intercept[CosmosException](Await.result(output))

    assertResult(Status.Forbidden)(exception.status)
    assert(exception.error.isInstanceOf[ResourceTooLarge])
  }

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
