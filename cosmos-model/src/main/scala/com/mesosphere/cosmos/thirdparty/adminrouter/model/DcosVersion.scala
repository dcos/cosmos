package com.mesosphere.cosmos.thirdparty.adminrouter.model

import com.mesosphere.universe.v3.DcosReleaseVersion

case class DcosVersion(
  version: DcosReleaseVersion,
  dcosImageCommit: String,
  bootstrapId: String
)
