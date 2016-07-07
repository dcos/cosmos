package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.{Bijection, Injection}
import org.scalatest.FreeSpec

import scala.util.{Failure, Success, Try}

final class ReleaseVersionConverterSpec extends FreeSpec {

  "v2ReleaseVersionToString should" - {

    "always succeed in the forward direction" in {
      assertResult("0")(universe.v2.model.ReleaseVersion("0").as[String])
    }

    "always succeed in the reverse direction" in {
      assertResult(universe.v2.model.ReleaseVersion("1"))("1".as[universe.v2.model.ReleaseVersion])
    }

  }

  "v3ReleaseVersionToInt should" - {

    "always succeed in the forward direction" in {
      val version = universe.v3.model.PackageDefinition.ReleaseVersion(2).get
      assertResult(2)(version.as[Int])
    }

    "succeed in the reverse direction on nonnegative numbers" in {
      val version = universe.v3.model.PackageDefinition.ReleaseVersion(0).get
      assertResult(Success(version))(0.as[Try[universe.v3.model.PackageDefinition.ReleaseVersion]])
    }

    "fail in the reverse direction on negative numbers" in {
      val message = "Expected integer value >= 0 for release version, but found [-1]"
      assertResult(Failure(ConversionFailure(message))) {
        (-1).as[Try[universe.v3.model.PackageDefinition.ReleaseVersion]]
      }
    }

  }

  "v3ReleaseVersionToString should" - {
    behave like v3ReleaseVersionStringConversions[String]
  }

  "v3ReleaseVersionToV2ReleaseVersion should" - {
    behave like v3ReleaseVersionStringConversions[universe.v2.model.ReleaseVersion]
  }

  private[this] def v3ReleaseVersionStringConversions[A](implicit
    versionToA: Injection[universe.v3.model.PackageDefinition.ReleaseVersion, A],
    aToString: Bijection[A, String]
  ): Unit = {

    "always succeed in the forward direction" in {
      assertResult("42")(universe.v3.model.PackageDefinition.ReleaseVersion(42).get.as[A].as[String])
    }

    "succeed in the reverse direction on nonnegative version numbers" in {
      assertResult(Success(universe.v3.model.PackageDefinition.ReleaseVersion(24).get)) {
        "24".as[A].as[Try[universe.v3.model.PackageDefinition.ReleaseVersion]]
      }
    }

    "fail in the reverse direction on negative version numbers" in {
      val message = "Expected integer value >= 0 for release version, but found [-2]"
      assertResult(Failure(ConversionFailure(message))) {
        "-2".as[A].as[Try[universe.v3.model.PackageDefinition.ReleaseVersion]]
      }
    }

    "fail in the reverse direction on non-number values" in {
      val message = "Expected integer value >= 0 for release version, but found [foo]"
      assertResult(Failure(ConversionFailure(message))) {
        "foo".as[A].as[Try[universe.v3.model.PackageDefinition.ReleaseVersion]]
      }
    }

  }

}
