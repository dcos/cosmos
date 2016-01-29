package com.mesosphere.cosmos.model

import io.circe.Json

case class ListResponse(
  packages: Seq[Installation]
)

case class Installation(
  appId: String,
  packageInformation: PackageInformation
)

case class PackageInformation(
  releaseVersion: String,
  packageSource: String,
  packageDefinition: PackageDefinition,
  resourceDefinition: Option[Resource] = None,
  commandDefinition: Option[Json] = None,
  configDefinition: Option[Json] = None
)
