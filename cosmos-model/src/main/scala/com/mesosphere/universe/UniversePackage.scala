package com.mesosphere.universe

import com.twitter.io.Buf.ByteBuffer
import io.circe.JsonObject

final case class UniversePackage(
  packageId: PackageId,
  packageJson: PackageDetails,
  marathonJsonMustache: ByteBuffer,
  commandJson: Option[Command],
  configJson: Option[JsonObject],
  resources: Seq[ResourceId]
)
