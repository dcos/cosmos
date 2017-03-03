package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe

// TODO: This should match the response type for update
// TODO: Why is appId optional?
case class ServiceStartResponse(
  packageName: String,
  packageVersion: universe.v3.model.PackageDefinition.Version,
  appId: Option[AppId] = None
)
