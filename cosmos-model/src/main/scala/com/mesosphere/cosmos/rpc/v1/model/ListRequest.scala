package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId

case class ListRequest(
  packageName: Option[String] = None,
  appId: Option[AppId] = None
)
