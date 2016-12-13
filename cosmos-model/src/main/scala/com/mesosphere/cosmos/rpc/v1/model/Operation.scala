package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe
import java.util.UUID

sealed trait Operation

case class Install(
  stagedPackageId: UUID,
  v3Package: universe.v3.model.V3Package
) extends Operation

case class UniverseInstall(
  packageDefinition: universe.v3.model.V3Package
) extends Operation

case class Uninstall(
  packageDefinition: Option[universe.v3.model.V3Package]
) extends Operation
