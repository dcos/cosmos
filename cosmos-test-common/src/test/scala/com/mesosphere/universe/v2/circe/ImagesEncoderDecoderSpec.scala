package com.mesosphere.universe.v2.circe

import com.mesosphere.universe.v2.model.Images
import io.circe.Decoder
import io.circe.Json
import io.circe.syntax._
import org.scalatest.FreeSpec

class ImagesEncoderDecoderSpec extends FreeSpec {
  "Images" - {
    val json = Json.obj(
      "icon-small" -> "http://some.place/icon-small.png".asJson,
      "icon-medium" -> "http://some.place/icon-medium.png".asJson,
      "icon-large" -> Json.Null,
      "screenshots" -> List(
        "http://some.place/screenshot-1.png",
        "http://some.place/screenshot-2.png"
      ).asJson
    )
    val images = Images(
      iconSmall = Some("http://some.place/icon-small.png"),
      iconMedium = Some("http://some.place/icon-medium.png"),
      iconLarge = None,
      screenshots = Some(List(
        "http://some.place/screenshot-1.png",
        "http://some.place/screenshot-2.png"
      ))
    )
    "encoder" in {
      assertResult(json)(images.asJson)
    }
    "decode" in {
      assertResult(images)(decodeJson[Images](json))
    }
    "round-trip" in {
      assertResult(images)(decodeJson[Images](images.asJson))
    }
  }

  private[this] def decodeJson[A: Decoder](json: Json)(implicit decoder: Decoder[A]): A = {
    decoder.decodeJson(json).getOrElse(throw new AssertionError("Unable to decode"))
  }

}
