package com.mesosphere.universe

import com.twitter.util.Future

/** The interface to our parent Universe repository. */
trait UpstreamUniverse {

  /** Obtains all upstream package IDs that fall within the provided scope. */
  def observablePackages(scope: PackageScope): Future[List[PackageId]]

  /** Provides detailed information for the given package. */
  def describePackageForSync(packageId: PackageId): Future[Option[UniversePackage]]

  /** Translates the resource name of a Docker image into its official Docker image name */
  def resolveDockerImage(imageResourceName: String): Future[Option[DockerImageName]]

}
