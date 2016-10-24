package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.model.PackageDetailsVersion
import io.circe.JsonObject

case class RunRequest(
  packageName: String,
  packageVersion: Option[PackageDetailsVersion] = None,
  options: Option[JsonObject] = None,
  appId: Option[AppId] = None
)
