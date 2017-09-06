package com.mesosphere.universe.v2.model

import com.netaporter.uri.Uri
import io.circe.Decoder
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import com.mesosphere.cosmos.circe.Encoders.encodeUri
import com.mesosphere.cosmos.circe.Decoders.decodeUri

case class PackageFiles(
  revision: String,
  sourceUri: Uri,
  packageJson: PackageDetails,
  marathonJsonMustache: String,
  commandJson: Option[Command] = None,
  configJson: Option[JsonObject] = None,
  resourceJson: Option[Resource] = None
)

object PackageFiles {
  implicit val encodeV2PackageFiles: Encoder[PackageFiles] = deriveEncoder[PackageFiles]
  implicit val decodeV2PackageFiles: Decoder[PackageFiles] = deriveDecoder[PackageFiles]
}
