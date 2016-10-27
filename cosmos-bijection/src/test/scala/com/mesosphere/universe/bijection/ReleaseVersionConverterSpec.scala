package com.mesosphere.universe.bijection

import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
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
      val Failure(iae) = (-1).as[Try[universe.v3.model.PackageDefinition.ReleaseVersion]]
      val expectedMessage = "Expected integer value >= 0 for release version, but found [-1]"
      assertResult(expectedMessage)(iae.getMessage)
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
      val version = 42
      assertResult("42") {
        universe.v3.model.PackageDefinition.ReleaseVersion(version).get.as[A].as[String]
      }
    }

    "succeed in the reverse direction on nonnegative version numbers" in {
      val version = 24
      assertResult(Success(universe.v3.model.PackageDefinition.ReleaseVersion(version).get)) {
        "24".as[A].as[Try[universe.v3.model.PackageDefinition.ReleaseVersion]]
      }
    }

    "fail in the reverse direction on negative version numbers" in {
      val Failure(iae) = "-2".as[A].as[Try[universe.v3.model.PackageDefinition.ReleaseVersion]]
      val message = "Expected integer value >= 0 for release version, but found [-2]"
      assertResult(message)(iae.getMessage)
    }

    "fail in the reverse direction on non-number values" in {
      val Failure(iae) = "foo".as[A].as[Try[universe.v3.model.PackageDefinition.ReleaseVersion]]
      val message = "Failed to invert: foo"
      assertResult(message)(iae.getMessage)
    }

  }

}
