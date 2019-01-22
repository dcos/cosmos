package com.mesosphere.cosmos.handler

import com.mesosphere.Generators.Implicits._
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.GenericHttpError
import com.mesosphere.cosmos.error.ResourceTooLarge
import io.lemonlabs.uri.Uri
import com.twitter.conversions.storage._
import com.twitter.finagle.http.Response
import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.StorageUnit
import io.finch.Output
import io.netty.handler.codec.http.HttpResponseStatus
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

final class ResourceProxyHandlerSpec extends FreeSpec with PropertyChecks with Matchers {

  type TestData = (StorageUnit, Long, Uri)

  "When Content-Length is provided by the upstream server" - {

    "When Content-Length matches the actual content stream length" - {

      val maxLengthLimit = 100
      val genTestData: Gen[TestData] = for {
        lengthLimit <- Gen.chooseNum(2, maxLengthLimit)
        actualLength <- Gen.chooseNum(1, lengthLimit - 1)
        uri <- arbitrary[Uri]
      } yield {
        (lengthLimit.bytes, actualLength.toLong, uri)
      }

      "Succeeds if Content-Length is below the limit" in {
        forAll(genTestData) { case (lengthLimit, actualLength, uri) =>
          ResourceProxyHandler.validateContentLength(uri, Some(actualLength), lengthLimit)
        }
      }

      "Fails if Content-Length is" - {

        "at the limit" in {
          forAll (genTestData) { case (lengthLimit, _, uri) =>
            val exception = intercept[CosmosException](ResourceProxyHandler.validateContentLength(uri, Some(lengthLimit.bytes), lengthLimit))
            assert(exception.error.isInstanceOf[ResourceTooLarge])
          }
        }

        "is above the limit" in {
          forAll (genTestData) { case (lengthLimit, _, uri) =>
            val exception = intercept[CosmosException](ResourceProxyHandler.validateContentLength(uri, Some(lengthLimit.bytes + 1), lengthLimit))
            assert(exception.error.isInstanceOf[ResourceTooLarge])
          }
        }

        "is zero" in {
          forAll (genTestData) { case (lengthLimit, _, uri) =>
            val exception = intercept[CosmosException](ResourceProxyHandler.validateContentLength(uri, Some(0), lengthLimit))
            assert(exception.error.isInstanceOf[GenericHttpError])
          }
        }
      }
    }

  }

  "Fails when Content-Length is not provided by the upstream server" in {
    val exception = intercept[CosmosException](ResourceProxyHandler.validateContentLength(Uri.parse("/random"), None, 1.bytes))
    assert(exception.error.isInstanceOf[GenericHttpError])
  }

  "Parses the filename correctly" in {

    ResourceProxyHandler.getFileNameFromUrl(
      Uri.parse("http://doesntreallymatter.com/c.d")
    ) shouldEqual Some("c.d")

    ResourceProxyHandler.getFileNameFromUrl(
      Uri.parse("http://doesntreallymatter.com/a/b/c.d")
    ) shouldEqual Some("c.d")

    ResourceProxyHandler.getFileNameFromUrl(
      Uri.parse("http://doesntreallymatter.com/a/b/c")
    ) shouldEqual Some("c")

    ResourceProxyHandler.getFileNameFromUrl(
      Uri.parse("http://doesntreallymatter.com/a/b/c/")
    ) shouldEqual Some("c")

    // These should never happen, but just in case.

    ResourceProxyHandler.getFileNameFromUrl(
      Uri.parse("http://doesntreallymatter.com/")
    ) shouldEqual None

    ResourceProxyHandler.getFileNameFromUrl(
      Uri.parse("https://doesntreallymatter.com")
    ) shouldEqual None

  }

  def assertFailure(output: Future[Output[Response]]): Assertion = {
    val exception = intercept[CosmosException](Await.result(output))
    assertResult(HttpResponseStatus.FORBIDDEN)(exception.error.status)
    assert(exception.error.isInstanceOf[ResourceTooLarge])
  }
}
