package com.mesosphere.cosmos.model

case class Installation(
  appId: AppId,
  packageInformation: Option[InstalledPackageInformation]
)
