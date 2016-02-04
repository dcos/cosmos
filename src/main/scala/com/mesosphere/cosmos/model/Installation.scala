package com.mesosphere.cosmos.model

case class Installation(
  appId: String,
  packageInformation: InstalledPackageInformation
)
