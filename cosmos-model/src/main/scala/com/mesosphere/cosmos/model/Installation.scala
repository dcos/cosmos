package com.mesosphere.cosmos.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId

case class Installation(
  appId: AppId,
  packageInformation: InstalledPackageInformation
)
