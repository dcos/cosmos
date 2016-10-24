package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.model.PackageDetailsVersion

case class KillResult(
  packageName: String,
  appId: AppId,
  packageVersion: Option[PackageDetailsVersion],
  postUninstallNotes: Option[String]
)
