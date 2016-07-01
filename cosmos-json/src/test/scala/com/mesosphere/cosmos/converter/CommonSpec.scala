package com.mesosphere.cosmos.converter

import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.{Bijection, Injection}
import org.scalatest.FreeSpec

import scala.util.{Failure, Success, Try}

final class CommonSpec extends FreeSpec {

  "v2PackagingVersionToString should" - {

    "succeed in the forward direction" in {
      assertResult("2.0")(universe.v2.model.PackagingVersion("2.0").as[String])
    }

    "succeed in the reverse direction" in {
      assertResult(universe.v2.model.PackagingVersion("3.0")) {
        "3.0".as[universe.v2.model.PackagingVersion]
      }
    }

  }

  "v3V2PackagingVersionToString should" - {
    behave like v3V2PackagingVersionConversions[String]
  }

  "v3V2PackagingVersionToV2PackagingVersion should" - {
    behave like v3V2PackagingVersionConversions[universe.v2.model.PackagingVersion]
  }

  "v3V3PackagingVersionToString should" - {
    behave like v3V3PackagingVersionConversions[String]
  }

  "v3V3PackagingVersionToV2PackagingVersion should" - {
    behave like v3V3PackagingVersionConversions[universe.v2.model.PackagingVersion]
  }

  "v3PackagingVersionToString should" - {
    behave like v3PackagingVersionConversions[String]
  }

  "v3PackagingVersionToV2PackagingVersion should" - {
    behave like v3PackagingVersionConversions[universe.v2.model.PackagingVersion]
  }

  private[this] def v3V2PackagingVersionConversions[A](implicit
    v2ToA: Injection[universe.v3.model.V2PackagingVersion.type, A],
    aToString: Bijection[A, String]
  ): Unit = {

    "succeed in the forward direction" in {
      assertResult("2.0")(universe.v3.model.V2PackagingVersion.as[A].as[String])
    }

    """succeed in the reverse direction if the version is "2.0"""" in {
      assertResult(Success(universe.v3.model.V2PackagingVersion)) {
        "2.0".as[A].as[Try[universe.v3.model.V2PackagingVersion.type]]
      }
    }

    "fail in the reverse direction if the string is anything else" in {
      val message = "Expected value [2.0] for packaging version, but found [3.0]"
      assertResult(Failure(ConversionFailure(message))) {
        "3.0".as[A].as[Try[universe.v3.model.V2PackagingVersion.type]]
      }
    }

  }

  private[this] def v3V3PackagingVersionConversions[A](implicit
    v3ToA: Injection[universe.v3.model.V3PackagingVersion.type, A],
    aToString: Bijection[A, String]
  ): Unit = {

    "succeed in the forward direction" in {
      assertResult("3.0")(universe.v3.model.V3PackagingVersion.as[A].as[String])
    }

    """succeed in the reverse direction if the string is "3.0"""" in {
      assertResult(Success(universe.v3.model.V3PackagingVersion)) {
        "3.0".as[A].as[Try[universe.v3.model.V3PackagingVersion.type]]
      }
    }

    "fail in the reverse direction if the string is anything else" in {
      val message = "Expected value [3.0] for packaging version, but found [2.0]"
      assertResult(Failure(ConversionFailure(message))) {
        "2.0".as[A].as[Try[universe.v3.model.V3PackagingVersion.type]]
      }
    }

  }

  private[this] def v3PackagingVersionConversions[A](implicit
    versionToA: Injection[universe.v3.model.PackagingVersion, A],
    aToString: Bijection[A, String]
  ): Unit = {

    "succeed in the forward direction" - {

      "V2PackagingVersion" in {
        val version: universe.v3.model.PackagingVersion = universe.v3.model.V2PackagingVersion
        assertResult("2.0")(version.as[A].as[String])
      }

      "V3PackagingVersion" in {
        val version: universe.v3.model.PackagingVersion = universe.v3.model.V3PackagingVersion
        assertResult("3.0")(version.as[A].as[String])
      }

    }

    "succeed in the reverse direction" - {

      "if the version is 2.0" in {
        assertResult(Success(universe.v3.model.V2PackagingVersion)) {
          "2.0".as[A].as[Try[universe.v3.model.PackagingVersion]]
        }
      }

      "if the version is 3.0" in {
        assertResult(Success(universe.v3.model.V3PackagingVersion)) {
          "3.0".as[A].as[Try[universe.v3.model.PackagingVersion]]
        }
      }

    }

    "fail in the reverse direction if the version is not 2.0 or 3.0" in {
      val message = "Expected one of [2.0, 3.0] for packaging version, but found [2.5]"
      assertResult(Failure(ConversionFailure(message))) {
        "2.5".as[A].as[Try[universe.v3.model.PackagingVersion]]
      }
    }

  }

}
