package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe.v2.model.{PackageDetails, Resource}

case class InstalledPackageInformation(
  packageDefinition: PackageDetails,
  resourceDefinition: Option[Resource] = None
)
