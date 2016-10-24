package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.model.PackageDetailsVersion

case class RunResponse(
  packageName: String,
  packageVersion: PackageDetailsVersion,
  appId: AppId
)
