package com.mesosphere.universe

import com.netaporter.uri.Uri
import io.circe.JsonObject

case class PackageFiles(
  revision: String,
  sourceUri: Uri,
  packageJson: PackageDetails,
  marathonJsonMustache: String,
  commandJson: Option[Command] = None,
  configJson: Option[JsonObject] = None,
  resourceJson: Option[Resource] = None
)
