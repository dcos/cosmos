package com.mesosphere.cosmos.model.thirdparty.adminrouter

import com.mesosphere.universe.v3.DcosReleaseVersion

case class DcosVersion(
  version: DcosReleaseVersion,
  dcosImageCommit: String,
  bootstrapId: String
)
