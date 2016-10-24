package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe

final case class PackageCoordinate(name: String, version: universe.v3.model.PackageDefinition.Version)
