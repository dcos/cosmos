package com.mesosphere.cosmos.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.model.PackageDetailsVersion

case class InstallResponse(
  packageName: String,
  packageVersion: PackageDetailsVersion,
  appId: AppId
)
