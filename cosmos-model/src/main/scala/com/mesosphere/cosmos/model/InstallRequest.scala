package com.mesosphere.cosmos.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.PackageDetailsVersion
import io.circe.JsonObject

case class InstallRequest(
  packageName: String,
  packageVersion: Option[PackageDetailsVersion] = None,
  options: Option[JsonObject] = None,
  appId: Option[AppId] = None
)
