package com.mesosphere.cosmos.thirdparty.adminrouter.circe

import cats.syntax.either._
import com.mesosphere.cosmos.thirdparty.adminrouter.model.DcosVersion
import com.mesosphere.universe
import com.mesosphere.universe.v3.circe.Decoders._
import io.circe.Decoder
import io.circe.HCursor

object Decoders {

  implicit val decodeDcosVersion: Decoder[DcosVersion] = Decoder.instance { (cursor: HCursor) =>
    for {
      v <- cursor.downField("version").as[universe.v3.model.DcosReleaseVersion]
      dIC <- cursor.downField("dcos-image-commit").as[String]
      bId <- cursor.downField("bootstrap-id").as[String]
    } yield DcosVersion(v, dIC, bId)
  }

}
