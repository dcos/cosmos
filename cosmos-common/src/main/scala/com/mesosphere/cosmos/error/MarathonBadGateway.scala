package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import com.twitter.finagle.http.Status
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class MarathonBadGateway(marathonStatus: Status) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Received response status code ${marathonStatus.code} from Marathon"
  }

  override def exception: CosmosException = {
    exception(Status.BadGateway, Map.empty, None)
  }
}

object MarathonBadGateway {
  implicit val encoder: Encoder[MarathonBadGateway] = deriveEncoder
}
