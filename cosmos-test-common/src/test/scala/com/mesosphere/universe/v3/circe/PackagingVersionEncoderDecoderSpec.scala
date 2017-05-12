package com.mesosphere.universe.v3.circe

import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import io.circe.Decoder
import io.circe.Json
import io.circe.syntax._
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import scala.util.Left
import scala.util.Right

final class PackagingVersionEncoderDecoderSpec extends FreeSpec with Matchers with TableDrivenPropertyChecks {

  "Encoders.encodePackagingVersion" - {
    "as PackagingVersion type" in {
      forAll(TestingPackages.validPackagingVersions) { (version, string) =>
        version.asJson should be(Json.fromString(string))
      }
    }

    "as subclass type" in {
      forAll(TestingPackages.validPackagingVersions) { (version, string) =>
        toJsonAsPackagingVersionSubclass(version) should be(Json.fromString(string))
      }
    }
  }

  "Decoders.decodeV3PackagingVersion should" - {
    "successfully decode packaging versions" in {
      forAll(TestingPackages.validPackagingVersions) { (version, string) =>
        val decodedVersion =
          Decoder[universe.v4.model.PackagingVersion].decodeJson(Json.fromString(string))
        decodedVersion should be (Right(version))
      }
    }

    "fail to decode any other string value" in {
      val invalidVersion = "3.1"
      val Left(failure) =
        Decoder[universe.v4.model.PackagingVersion].decodeJson(Json.fromString(invalidVersion))
      val expectedMessage =
        TestingPackages.renderInvalidVersionMessage(invalidVersion)
      assertResult(expectedMessage)(failure.message)
    }

    behave like failedDecodeOnNonString[universe.v4.model.PackagingVersion]

  }

  "Decoders.decodeV3V2PackagingVersion should" - {

    "successfully decode version 2.0" in {
      assertResult(Right(universe.v3.model.V2PackagingVersion)) {
        Decoder[universe.v3.model.V2PackagingVersion.type].decodeJson(Json.fromString("2.0"))
      }
    }

    "fail to decode version 3.0" in {
      val Left(failure) = Decoder[universe.v3.model.V2PackagingVersion.type].decodeJson(
        Json.fromString("3.0")
      )
      assertResult("Expected value [2.0] for packaging version, but found [3.0]")(failure.message)
    }

    "fail to decode any other value" in {
      val Left(failure) = Decoder[universe.v3.model.V2PackagingVersion.type].decodeJson(
        Json.fromString("2.1")
      )
      assertResult("Expected value [2.0] for packaging version, but found [2.1]")(failure.message)
    }

    behave like failedDecodeOnNonString[universe.v3.model.V2PackagingVersion.type]

  }

  "Decoders.decodeV3V3PackagingVersion should" - {

    "successfully decode version 3.0" in {
      assertResult(Right(universe.v3.model.V3PackagingVersion)) {
        Decoder[universe.v3.model.V3PackagingVersion.type].decodeJson(Json.fromString("3.0"))
      }
    }

    "fail to decode version 2.0" in {
      val Left(failure) = Decoder[universe.v3.model.V3PackagingVersion.type].decodeJson(
        Json.fromString("2.0")
      )
      assertResult("Expected value [3.0] for packaging version, but found [2.0]")(failure.message)
    }

    "fail to decode any other value" in {
      val Left(failure) = Decoder[universe.v3.model.V3PackagingVersion.type].decodeJson(
        Json.fromString("4.0")
      )
      assertResult("Expected value [3.0] for packaging version, but found [4.0]")(failure.message)
    }

    behave like failedDecodeOnNonString[universe.v3.model.V3PackagingVersion.type]

  }

  "Decoders.decodeV4V4PackagingVersion should" - {

    "successfully decode version 4.0" in {
      assertResult(Right(universe.v4.model.V4PackagingVersion)) {
        Decoder[universe.v4.model.V4PackagingVersion.type].decodeJson(Json.fromString("4.0"))
      }
    }

    "fail to decode version 2.0" in {
      val Left(failure) = Decoder[universe.v4.model.V4PackagingVersion.type].decodeJson(
        Json.fromString("2.0")
      )
      assertResult("Expected value [4.0] for packaging version, but found [2.0]")(failure.message)
    }

    "fail to decode any other value" in {
      val Left(failure) = Decoder[universe.v4.model.V4PackagingVersion.type].decodeJson(
        Json.fromString("3.0")
      )
      assertResult("Expected value [4.0] for packaging version, but found [3.0]")(failure.message)
    }

    behave like failedDecodeOnNonString[universe.v4.model.V4PackagingVersion.type]

  }

  private[this] def failedDecodeOnNonString[A](implicit decoder: Decoder[A]): Unit = {
    "fail to decode any non-string JSON value" in {
      val Left(failure) = decoder.decodeJson(Json.fromDoubleOrNull(3.1))
      assertResult("String value expected")(failure.message)
    }
  }

  private[this] def toJsonAsPackagingVersionSubclass(
    packagingVersion: universe.v4.model.PackagingVersion
  ): Json = packagingVersion match {
    case universe.v3.model.V2PackagingVersion => universe.v3.model.V2PackagingVersion.asJson
    case universe.v3.model.V3PackagingVersion => universe.v3.model.V3PackagingVersion.asJson
    case universe.v4.model.V4PackagingVersion => universe.v4.model.V4PackagingVersion.asJson
  }

}
