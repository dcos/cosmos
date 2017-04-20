package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe.v2.model.Resource

case class InstalledPackageInformation(
  packageDefinition: InstalledPackageInformationPackageDetails,
  resourceDefinition: Option[Resource] = None
)
