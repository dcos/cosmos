package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe

// TODO: replace with the class in the install-queue branch
final case class PackageCoordinate(name: String, version: universe.v3.model.PackageDefinition.Version)
