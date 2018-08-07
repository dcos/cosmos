package com.mesosphere.universe.bijection

import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.test.TestingPackages
import com.twitter.bijection.Bijection
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.Injection
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import scala.util.Failure
import scala.util.Success
import scala.util.Try

final class PackagingVersionConverterSpec extends FreeSpec with Matchers with TableDrivenPropertyChecks {

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

  "v4V4PackagingVersionToString should" - {
    behave like v4V4PackagingVersionConversions[String]
  }

  "v4V4PackagingVersionToV2PackagingVersion should" - {
    behave like v4V4PackagingVersionConversions[universe.v2.model.PackagingVersion]
  }

  "v5V5PackagingVersionToV2PackagingVersion should" - {
    behave like v5V5PackagingVersionConversions[universe.v2.model.PackagingVersion]
  }

  "v5V5PackagingVersionToString should" - {
    behave like v5V5PackagingVersionConversions[String]
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
      val Failure(iae) = "3.0".as[A].as[Try[universe.v3.model.V2PackagingVersion.type]]
      val message = "Expected value [2.0] for packaging version, but found [3.0]"
      assertResult(message)(iae.getMessage)
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
      val Failure(iae) = "2.0".as[A].as[Try[universe.v3.model.V3PackagingVersion.type]]
      val message = "Expected value [3.0] for packaging version, but found [2.0]"
      assertResult(message)(iae.getMessage)
    }

  }

  private[this] def v4V4PackagingVersionConversions[A](implicit
    packagingVersionConverter: Injection[universe.v4.model.V4PackagingVersion.type, A],
    aToString: Bijection[A, String]
  ): Unit = {

    "succeed in the forward direction" in {
      assertResult("4.0")(universe.v4.model.V4PackagingVersion.as[A].as[String])
    }

    """succeed in the reverse direction if the string is "4.0"""" in {
      assertResult(Success(universe.v4.model.V4PackagingVersion)) {
        "4.0".as[A].as[Try[universe.v4.model.V4PackagingVersion.type]]
      }
    }

    "fail in the reverse direction if the string is anything else" in {
      val Failure(iae) = "2.0".as[A].as[Try[universe.v4.model.V4PackagingVersion.type]]
      val message = "Expected value [4.0] for packaging version, but found [2.0]"
      assertResult(message)(iae.getMessage)
    }

  }

  private[this] def v5V5PackagingVersionConversions[A](implicit
   packagingVersionConverter: Injection[universe.v5.model.V5PackagingVersion.type, A],
   aToString: Bijection[A, String]
  ): Unit = {

    "succeed in the forward direction" in {
      assertResult("5.0")(universe.v5.model.V5PackagingVersion.as[A].as[String])
    }

    """succeed in the reverse direction if the string is "5.0"""" in {
      assertResult(Success(universe.v5.model.V5PackagingVersion)) {
        "4.0".as[A].as[Try[universe.v5.model.V5PackagingVersion.type]]
      }
    }

    "fail in the reverse direction if the string is anything else" in {
      val Failure(iae) = "2.0".as[A].as[Try[universe.v5.model.V5PackagingVersion.type]]
      val message = "Expected value [5.0] for packaging version, but found [2.0]"
      assertResult(message)(iae.getMessage)
    }

  }

  private[this] def v3PackagingVersionConversions[A](implicit
    versionToA: Injection[universe.v4.model.PackagingVersion, A],
    aToString: Bijection[A, String]
  ): Unit = {

    "succeed in the forward direction" in {
      forAll(TestingPackages.validPackagingVersions) { (version, string) =>
        version.as[A].as[String] should be (string)
      }
    }

    "succeed in the reverse direction" - {
      "when the version is valid" in {
        forAll(TestingPackages.validPackagingVersions) { (version, string) =>
          string.as[A].as[Try[universe.v4.model.PackagingVersion]] should be (Success(version))
        }
      }
    }

    "fail in the reverse direction if the version is not 2.0 or 3.0 or 4.0" in {
      val invalidVersion = "2.5"
      val Failure(iae) = invalidVersion.as[A].as[Try[universe.v4.model.PackagingVersion]]
      val message = TestingPackages.renderInvalidVersionMessage(invalidVersion)
      assertResult(message)(iae.getMessage)
    }

  }

}
