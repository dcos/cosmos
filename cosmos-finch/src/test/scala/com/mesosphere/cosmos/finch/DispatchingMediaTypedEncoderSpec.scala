package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.{CompoundMediaType, MediaType}
import io.circe.syntax._
import io.circe.{Encoder, Json}
import org.scalatest.FreeSpec

final class DispatchingMediaTypedEncoderSpec extends FreeSpec {

  import DispatchingMediaTypedEncoderSpec._

  "encoderFor(MediaType) should" - {

    "return the first encoder with a media type compatible with the argument" in {
      val Some(mediaTypedEncoder) = ThreeElementEncoder(CompoundMediaType(MediaType("foo", "bar")))
      assertResult(Json.fromInt(1))(mediaTypedEncoder.encoder(()))
      assertResult(MediaType("foo", "bar"))(mediaTypedEncoder.mediaType)
    }

    "indicate failure if no compatible encoder is found" - {

      "because there are no encoders" in {
        val dispatchingEncoder = DispatchingMediaTypedEncoder(Set.empty[MediaTypedEncoder[String]])
        assertResult(None)(dispatchingEncoder(CompoundMediaType(TestingMediaTypes.applicationJson)))
      }

      "because there are only incompatible encoders" in {
        assertResult(None)(ThreeElementEncoder(CompoundMediaType(TestingMediaTypes.applicationJson)))
      }

    }

  }

  "mediaTypes should return the media types of each of the encoders" - {
    "zero elements" in {
      assertResult(Set.empty)(DispatchingMediaTypedEncoder(Set.empty[MediaTypedEncoder[String]]).mediaTypes)
    }

    "three elements" in {
      val expected = Set(MediaType("foo", "foo"), MediaType("foo", "bar"), MediaType("foo", "baz"))
      assertResult(expected)(ThreeElementEncoder.mediaTypes)
    }
  }

}

object DispatchingMediaTypedEncoderSpec {

  val ThreeElementEncoder: DispatchingMediaTypedEncoder[Unit] = DispatchingMediaTypedEncoder(Set(
    MediaTypedEncoder(Encoder.instance[Unit](_ => 0.asJson), MediaType("foo", "foo")),
    MediaTypedEncoder(Encoder.instance[Unit](_ => 1.asJson), MediaType("foo", "bar")),
    MediaTypedEncoder(Encoder.instance[Unit](_ => 2.asJson), MediaType("foo", "baz"))
  ))

}
