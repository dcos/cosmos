package com.mesosphere.cosmos.model

import com.mesosphere.universe.PackageDetailsVersion

case class UninstallResult(
  packageName: String,
  appId: AppId,
  packageVersion: Option[PackageDetailsVersion],
  postUninstallNotes: Option[String]
)
