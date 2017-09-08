package com.mesosphere.cosmos.thirdparty.adminrouter.model

import com.mesosphere.universe
import io.circe.Decoder
import io.circe.HCursor

case class DcosVersion(
  version: universe.v3.model.DcosReleaseVersion,
  dcosImageCommit: String,
  bootstrapId: String
)

object DcosVersion {
  implicit val decodeDcosVersion: Decoder[DcosVersion] = Decoder.instance { (cursor: HCursor) =>
    for {
      v <- cursor.downField("version").as[universe.v3.model.DcosReleaseVersion]
      dIC <- cursor.downField("dcos-image-commit").as[String]
      bId <- cursor.downField("bootstrap-id").as[String]
    } yield DcosVersion(v, dIC, bId)
  }
}
