package com.mesosphere.cosmos.thirdparty.marathon.model

import io.circe.JsonObject

case class MarathonError(message: String, details: Option[List[JsonObject]])
