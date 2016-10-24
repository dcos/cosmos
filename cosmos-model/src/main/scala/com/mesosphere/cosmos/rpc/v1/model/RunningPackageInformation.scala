package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe.v2.model.Resource

case class RunningPackageInformation(
  packageDefinition: RunningPackageInformationPackageDetails,
  resourceDefinition: Option[Resource] = None
)
