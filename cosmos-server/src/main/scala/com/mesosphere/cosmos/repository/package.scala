package com.mesosphere.cosmos

import com.mesosphere.universe
import com.twitter.util.Future
import java.util.UUID

package object repository {
  type Installer = (UUID, universe.v3.model.PackageDefinition) => Future[Unit]

  type UniverseInstaller = universe.v3.model.PackageDefinition => Future[Unit]
  object UniverseInstaller {
    object Noop extends UniverseInstaller {
      def apply(p: universe.v3.model.PackageDefinition): Future[Unit] = Future.Done
    }
  }

  type Uninstaller = (
    rpc.v1.model.PackageCoordinate,
    Option[universe.v3.model.PackageDefinition]
  ) => Future[Unit]
  object Uninstaller {
    object Noop extends Uninstaller {
      def apply(
        pc: rpc.v1.model.PackageCoordinate,
        p: Option[universe.v3.model.PackageDefinition]
      ): Future[Unit] = Future.Done
    }
  }
}
