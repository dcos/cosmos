package com.mesosphere.universe.v3.circe

import com.mesosphere.universe
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.circe.Encoders._
import io.circe.Decoder
import io.circe.Json
import io.circe.syntax._
import org.scalatest.FreeSpec
import scala.util.Left
import scala.util.Right

final class PackagingVersionEncoderDecoderSpec extends FreeSpec {

  "Encoders.encodePackagingVersion" - {

    "for V2PackagingVersion as PackagingVersion" in {
      val version: universe.v3.model.PackagingVersion = universe.v3.model.V2PackagingVersion
      assertResult(Json.fromString("2.0"))(version.asJson)
    }

    "for V3PackagingVersion as PackagingVersion" - {
      val version: universe.v3.model.PackagingVersion = universe.v3.model.V3PackagingVersion
      assertResult(Json.fromString("3.0"))(version.asJson)
    }

    "for V2PackagingVersion as V2PackagingVersion" - {
      assertResult(Json.fromString("2.0"))(universe.v3.model.V2PackagingVersion.asJson)
    }

    "for V3PackagingVersion as V3PackagingVersion" - {
      assertResult(Json.fromString("3.0"))(universe.v3.model.V3PackagingVersion.asJson)
    }

  }

  "Decoders.decodeV3PackagingVersion should" - {

    "successfully decode version 2.0" in {
      assertResult(Right(universe.v3.model.V2PackagingVersion)) {
        Decoder[universe.v3.model.PackagingVersion].decodeJson(Json.fromString("2.0"))
      }
    }

    "successfully decode version 3.0" in {
      assertResult(Right(universe.v3.model.V3PackagingVersion)) {
        Decoder[universe.v3.model.PackagingVersion].decodeJson(Json.fromString("3.0"))
      }
    }

    "fail to decode any other string value" in {
      val Left(failure) = Decoder[universe.v3.model.PackagingVersion].decodeJson(Json.fromString("3.1"))
      assertResult("Expected one of [2.0, 3.0] for packaging version, but found [3.1]") {
        failure.message
      }
    }

    behave like failedDecodeOnNonString[universe.v3.model.PackagingVersion]

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

  private[this] def failedDecodeOnNonString[A](implicit decoder: Decoder[A]): Unit = {
    "fail to decode any non-string JSON value" in {
      val Left(failure) = decoder.decodeJson(Json.fromDoubleOrNull(3.1))
      assertResult("String value expected")(failure.message)
    }
  }

}
