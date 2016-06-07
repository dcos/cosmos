package com.mesosphere.cosmos.thirdparty.adminrouter.circe

import com.mesosphere.cosmos.thirdparty.adminrouter.model.DcosVersion
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.model.DcosReleaseVersion
import io.circe.{Decoder, HCursor}

object Decoders {

  implicit val decodeDcosVersion: Decoder[DcosVersion] = Decoder.instance { (cursor: HCursor) =>
    for {
      v <- cursor.downField("version").as[DcosReleaseVersion]
      dIC <- cursor.downField("dcos-image-commit").as[String]
      bId <- cursor.downField("bootstrap-id").as[String]
    } yield DcosVersion(v, dIC, bId)
  }

}
