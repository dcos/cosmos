package com.mesosphere.cosmos.model

import com.mesosphere.universe.PackageDetailsVersion

case class InstallResponse(
  packageName: String,
  packageVersion: PackageDetailsVersion,
  appId: AppId
)
