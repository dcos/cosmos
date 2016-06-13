package com.mesosphere.cosmos.rpc.v2.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe

case class InstallResponse(
  packageName: String,
  packageVersion: universe.v3.model.PackageDefinition.Version,
  appId: AppId,
  postInstallNotes: Option[String] = None,
  cli: Option[universe.v3.model.Cli] = None
)
