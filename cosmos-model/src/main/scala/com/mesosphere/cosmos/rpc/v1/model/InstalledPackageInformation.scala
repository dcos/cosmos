package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.label.v1.model.PackageMetadata
import com.mesosphere.universe.v2.model.Resource

case class InstalledPackageInformation(
  packageDefinition: PackageMetadata,
  resourceDefinition: Option[Resource] = None
)
