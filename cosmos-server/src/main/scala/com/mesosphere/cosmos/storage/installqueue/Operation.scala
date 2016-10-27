package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.universe
import com.netaporter.uri.Uri

sealed trait Operation

case class Install(
  uri: Uri,
  packageDefinition: universe.v3.model.PackageDefinition
) extends Operation

case class UniverseInstall(
  packageDefinition: universe.v3.model.PackageDefinition
) extends Operation

case class Uninstall(
  packageDefinition: Option[universe.v3.model.PackageDefinition]
) extends Operation
