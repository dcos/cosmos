package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonError
import io.circe.JsonObject
import io.circe.syntax._

final case class MarathonBadResponse(marathonError: MarathonError) extends CosmosError {
  override def data: Option[JsonObject] = {
    marathonError.details.map(details => JsonObject.singleton("errors", details.asJson))
  }
  override def message: String = marathonError.message
}
