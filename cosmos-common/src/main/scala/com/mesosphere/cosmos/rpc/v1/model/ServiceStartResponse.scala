package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe

case class ServiceStartResponse(
  packageName: String,
  packageVersion: universe.v3.model.Version,
  appId: Option[AppId] = None
)
