package com.mesosphere.cosmos.model.thirdparty.marathon

import io.circe.JsonObject

case class MarathonError(message: String, details: Option[List[JsonObject]])
