package com.mesosphere.cosmos.rpc.v1.model

import io.circe.JsonObject

case class RenderResponse(
  marathonJson: JsonObject
)
