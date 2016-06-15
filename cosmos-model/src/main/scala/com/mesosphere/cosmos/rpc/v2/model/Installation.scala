package com.mesosphere.cosmos.rpc.v2.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v3.model.PackageDefinition

case class Installation(
  appId: AppId,
  packageInformation: PackageDefinition
)
