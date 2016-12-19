package com.mesosphere.cosmos.storage.v1.model

import com.mesosphere.universe
import java.util.UUID

sealed trait Operation {
  val v3Package: universe.v3.model.V3Package
}

case class Install(
  stagedPackageId: UUID,
  v3Package: universe.v3.model.V3Package
) extends Operation

case class UniverseInstall(
  v3Package: universe.v3.model.V3Package
) extends Operation

case class Uninstall(
  v3Package: universe.v3.model.V3Package
) extends Operation
