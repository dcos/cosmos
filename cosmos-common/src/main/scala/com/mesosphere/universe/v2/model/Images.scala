package com.mesosphere.universe.v2.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.Json
import io.circe.syntax._

case class Images(
  iconSmall: Option[String],
  iconMedium: Option[String],
  iconLarge: Option[String],
  screenshots: Option[List[String]]
)

object Images {
  implicit val encodeImages: Encoder[Images] = Encoder.instance { (images: Images) =>
    Json.obj(
      "icon-small" -> images.iconSmall.asJson,
      "icon-medium" -> images.iconMedium.asJson,
      "icon-large" -> images.iconLarge.asJson,
      "screenshots" -> images.screenshots.asJson
    )
  }

  implicit val decodeV2Images: Decoder[Images] = Decoder.instance { (cursor: HCursor) =>
    for {
      iS <- cursor.downField("icon-small").as[Option[String]]
      iM <- cursor.downField("icon-medium").as[Option[String]]
      iL <- cursor.downField("icon-large").as[Option[String]]
      ss <- cursor.downField("screenshots").as[Option[List[String]]]
    } yield Images(iS, iM, iL, ss)
  }
}
