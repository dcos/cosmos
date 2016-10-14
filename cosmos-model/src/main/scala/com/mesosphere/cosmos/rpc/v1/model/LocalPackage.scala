package com.mesosphere.cosmos.rpc.v1.model

sealed trait LocalPackage

final case class NotInstalled(
  pkg: com.mesosphere.cosmos.rpc.v2.model.DescribeResponse
) extends LocalPackage

final case class Installing(
  pkg: com.mesosphere.cosmos.rpc.v2.model.DescribeResponse
) extends LocalPackage

final case class Installed(
  pkg: com.mesosphere.cosmos.rpc.v2.model.DescribeResponse
) extends LocalPackage

final case class Uninstalling(
  pkg: com.mesosphere.cosmos.rpc.v2.model.DescribeResponse
) extends LocalPackage

final case class Failed(
  operation: String, // TODO: Change this when we merge the PackageOps PR
  error: String, // TODO: Not sure this is correct; it needs to be the deserialized form CosmosError
  pkg: com.mesosphere.cosmos.rpc.v2.model.DescribeResponse
) extends LocalPackage

final case class Invalid(
  error: String, // TODO: Not sure this is correct; it needs to be the deserialized form
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
