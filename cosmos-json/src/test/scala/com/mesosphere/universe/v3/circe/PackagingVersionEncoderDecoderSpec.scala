package com.mesosphere.universe.v3.circe

import cats.data.Xor
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.universe.v3.model.{PackagingVersion, V2PackagingVersion, V3PackagingVersion}
import io.circe.syntax._
import io.circe.{Decoder, Json}
import org.scalatest.FreeSpec

final class PackagingVersionEncoderDecoderSpec extends FreeSpec {

  "Encoders.encodePackagingVersion" - {

    "for V2PackagingVersion as PackagingVersion" in {
      val version: PackagingVersion = V2PackagingVersion
      assertResult(Json.fromString("2.0"))(version.asJson)
    }

    "for V3PackagingVersion as PackagingVersion" - {
      val version: PackagingVersion = V3PackagingVersion
      assertResult(Json.fromString("3.0"))(version.asJson)
    }

    "for V2PackagingVersion as V2PackagingVersion" - {
      assertResult(Json.fromString("2.0"))(V2PackagingVersion.asJson)
    }

    "for V3PackagingVersion as V3PackagingVersion" - {
      assertResult(Json.fromString("3.0"))(V3PackagingVersion.asJson)
    }

  }

  "Decoders.decodeV3PackagingVersion should" - {

    "successfully decode version 2.0" in {
      assertResult(Xor.Right(V2PackagingVersion)) {
        Decoder[PackagingVersion].decodeJson(Json.fromString("2.0"))
      }
    }

    "successfully decode version 3.0" in {
      assertResult(Xor.Right(V3PackagingVersion)) {
        Decoder[PackagingVersion].decodeJson(Json.fromString("3.0"))
      }
    }

    "fail to decode any other string value" in {
      val Xor.Left(failure) = Decoder[PackagingVersion].decodeJson(Json.fromString("3.1"))
      assertResult("Expected one of [2.0, 3.0] for packaging version, but found [3.1]") {
        failure.message
      }
    }

    behave like failedDecodeOnNonString[PackagingVersion]

  }

  "Decoders.decodeV3V2PackagingVersion should" - {

    "successfully decode version 2.0" in {
      assertResult(Xor.Right(V2PackagingVersion)) {
        Decoder[V2PackagingVersion.type].decodeJson(Json.fromString("2.0"))
      }
    }

    "fail to decode version 3.0" in {
      val Xor.Left(failure) = Decoder[V2PackagingVersion.type].decodeJson(Json.fromString("3.0"))
      assertResult("Expected value [2.0] for packaging version, but found [3.0]")(failure.message)
    }

    "fail to decode any other value" in {
      val Xor.Left(failure) = Decoder[V2PackagingVersion.type].decodeJson(Json.fromString("2.1"))
      assertResult("Expected value [2.0] for packaging version, but found [2.1]")(failure.message)
    }

    behave like failedDecodeOnNonString[V2PackagingVersion.type]

  }

  "Decoders.decodeV3V3PackagingVersion should" - {

    "successfully decode version 3.0" in {
      assertResult(Xor.Right(V3PackagingVersion)) {
        Decoder[V3PackagingVersion.type].decodeJson(Json.fromString("3.0"))
      }
    }

    "fail to decode version 2.0" in {
      val Xor.Left(failure) = Decoder[V3PackagingVersion.type].decodeJson(Json.fromString("2.0"))
      assertResult("Expected value [3.0] for packaging version, but found [2.0]")(failure.message)
    }

    "fail to decode any other value" in {
      val Xor.Left(failure) = Decoder[V3PackagingVersion.type].decodeJson(Json.fromString("4.0"))
      assertResult("Expected value [3.0] for packaging version, but found [4.0]")(failure.message)
    }

    behave like failedDecodeOnNonString[V3PackagingVersion.type]

  }

  private[this] def failedDecodeOnNonString[A](implicit decoder: Decoder[A]): Unit = {

    "fail to decode any non-string JSON value" in {
      val Xor.Left(failure) = decoder.decodeJson(Json.fromDoubleOrNull(3.1))
      assertResult("String value expected")(failure.message)
    }

  }

}
