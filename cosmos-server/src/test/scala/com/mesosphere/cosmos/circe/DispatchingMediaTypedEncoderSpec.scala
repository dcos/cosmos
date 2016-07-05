package com.mesosphere.cosmos.circe

import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import io.circe.syntax._
import io.circe.{Encoder, Json}
import org.scalatest.FreeSpec

final class DispatchingMediaTypedEncoderSpec extends FreeSpec {

  import DispatchingMediaTypedEncoderSpec._

  "encoderFor(MediaType) should" - {

    "return the first encoder with a media type compatible with the argument" in {
      val Some(mediaTypedEncoder) = ThreeElementEncoder(MediaType("foo", "bar"))
      assertResult(Json.int(1))(mediaTypedEncoder.encoder(()))
      assertResult(MediaType("foo", "bar"))(mediaTypedEncoder.mediaType)
    }

    "indicate failure if no compatible encoder is found" - {

      "because there are no encoders" in {
        val dispatchingEncoder = DispatchingMediaTypedEncoder(Seq.empty)
        assertResult(None)(dispatchingEncoder(MediaTypes.applicationJson))
      }

      "because there are only incompatible encoders" in {
        assertResult(None)(ThreeElementEncoder(MediaTypes.applicationJson))
      }

    }

  }

  "mediaTypes should return the media types of each of the encoders" - {
    "zero elements" in {
      assertResult(Seq.empty)(DispatchingMediaTypedEncoder(Seq.empty).mediaTypes)
    }

    "three elements" in {
      val expected = Seq(MediaType("foo", "foo"), MediaType("foo", "bar"), MediaType("foo", "baz"))
      assertResult(expected)(ThreeElementEncoder.mediaTypes)
    }
  }

}

object DispatchingMediaTypedEncoderSpec {

  val ThreeElementEncoder: DispatchingMediaTypedEncoder[Unit] = DispatchingMediaTypedEncoder(Seq(
    MediaTypedEncoder(Encoder.instance[Unit](_ => 0.asJson), MediaType("foo", "foo")),
    MediaTypedEncoder(Encoder.instance[Unit](_ => 1.asJson), MediaType("foo", "bar")),
    MediaTypedEncoder(Encoder.instance[Unit](_ => 2.asJson), MediaType("foo", "baz"))
  ))

}
