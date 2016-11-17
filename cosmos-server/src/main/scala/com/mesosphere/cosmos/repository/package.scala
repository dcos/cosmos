package com.mesosphere.cosmos

import com.mesosphere.universe
import com.twitter.util.Future
import java.util.UUID

package object repository {
  type Installer = (UUID, universe.v3.model.PackageDefinition) => Future[Unit]

  type UniverseInstaller = universe.v3.model.PackageDefinition => Future[Unit]

  type Uninstaller = (
    rpc.v1.model.PackageCoordinate,
    Option[universe.v3.model.PackageDefinition]
  ) => Future[Unit]
}
