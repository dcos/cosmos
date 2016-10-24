package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe

sealed trait LocalPackage

final case class NotInstalled(
  metadata: universe.v3.model.PackageDefinition
) extends LocalPackage

final case class Installing(
  metadata: universe.v3.model.PackageDefinition
) extends LocalPackage

final case class Installed(
  metadata: universe.v3.model.PackageDefinition
) extends LocalPackage

final case class Uninstalling(
  metadata: universe.v3.model.PackageDefinition
) extends LocalPackage

final case class Failed(
  operation: String, // TODO: Change this when we merge the PackageOps PR
  error: ErrorResponse,
  metadata: universe.v3.model.PackageDefinition
) extends LocalPackage

final case class Invalid(
  error: ErrorResponse,
  packageCoordinate: PackageCoordinate
) extends LocalPackage

/* TODO: Talk about the serialized form
 * {
 *   "status": "not-installed",
 *   "pkg": ...,
 *   "operation": ...,
 *   "error": ...,
 *   "packageCoordinate": ...
 * }
 */
