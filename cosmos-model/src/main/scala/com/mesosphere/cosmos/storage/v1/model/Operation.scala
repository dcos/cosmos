package com.mesosphere.cosmos.storage.v1.model

import com.mesosphere.universe
import java.util.UUID

sealed trait Operation {
  val packageDefinition: universe.v3.model.SupportedPackageDefinition
}

case class Install(
  stagedPackageId: UUID,
  packageDefinition: universe.v3.model.SupportedPackageDefinition
) extends Operation

case class UniverseInstall(
  packageDefinition: universe.v3.model.SupportedPackageDefinition
) extends Operation

case class Uninstall(
  packageDefinition: universe.v3.model.SupportedPackageDefinition
) extends Operation
