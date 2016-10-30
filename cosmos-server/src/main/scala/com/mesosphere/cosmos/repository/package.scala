package com.mesosphere.cosmos

import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.util.Future

package object repository {
  type Installer = (Uri, universe.v3.model.PackageDefinition) => Future[Unit]

  type UniverseInstaller = universe.v3.model.PackageDefinition => Future[Unit]

  type Uninstaller = (
    rpc.v1.model.PackageCoordinate,
    Option[universe.v3.model.PackageDefinition]
  ) => Future[Unit]
}
