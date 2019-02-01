package com.mesosphere.cosmos.handler

import com.mesosphere.Generators.Implicits._
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.GenericHttpError
import io.lemonlabs.uri.Uri
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

final class ResourceProxyHandlerSpec extends FreeSpec with PropertyChecks with Matchers {

  type TestData = (Long, Uri)

  "When Content-Length is provided by the upstream server" - {

    "When Content-Length matches the actual content stream length" - {
      // scalastyle:off magic.number
      val genTestData: Gen[TestData] = for {
        actualLength <- Gen.chooseNum(2, 10)
        uri <- arbitrary[Uri]
      } yield {
        (actualLength.toLong, uri)
      }

      "Succeeds if Content-Length is below the limit" in {
        forAll(genTestData) { case (actualLength, uri) =>
          ResourceProxyHandler.validateContentLength(uri, Some(actualLength))
        }
      }

      "Fails if Content-Length is zero" in {
        forAll (genTestData) { case (_, uri) =>
          val exception = intercept[CosmosException](ResourceProxyHandler.validateContentLength(uri, Some(0)))
          assert(exception.error.isInstanceOf[GenericHttpError])
        }
      }
    }

  }

  "Fails when Content-Length is not provided by the upstream server" in {
    val exception = intercept[CosmosException](ResourceProxyHandler.validateContentLength(Uri.parse("/random"), None))
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
}
