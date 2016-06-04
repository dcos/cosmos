package com.mesosphere.cosmos.model

import com.mesosphere.universe.v2.{PackageDetails, Resource}

case class InstalledPackageInformation(
  packageDefinition: PackageDetails,
  resourceDefinition: Option[Resource] = None
)
