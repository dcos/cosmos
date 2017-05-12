package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe

sealed trait AddRequest

case class UniverseAddRequest(
  packageName: String,
  packageVersion: Option[universe.v3.model.Version]
) extends AddRequest

case class UploadAddRequest(packageData: Array[Byte]) extends AddRequest
