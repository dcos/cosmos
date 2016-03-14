package com.mesosphere.cosmos.model

import com.mesosphere.universe.{PackageDetails, Resource}

case class InstalledPackageInformation(
  packageDefinition: PackageDetails,
  resourceDefinition: Option[Resource] = None
)
