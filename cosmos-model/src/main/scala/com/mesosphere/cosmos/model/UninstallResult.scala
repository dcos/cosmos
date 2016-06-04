package com.mesosphere.cosmos.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.PackageDetailsVersion

case class UninstallResult(
  packageName: String,
  appId: AppId,
  packageVersion: Option[PackageDetailsVersion],
  postUninstallNotes: Option[String]
)
