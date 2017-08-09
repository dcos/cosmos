package com.mesosphere.cosmos.error


import com.twitter.util.Duration
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
import com.mesosphere.universe.common.circe.Encoders._

final case class TimeoutError(
  operation: String,
  destination: String,
  timeout: Duration
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"$operation timed out on $destination" +
    s" after ${timeout.inSeconds} seconds"
}

object TimeoutError {
  implicit val encoder: Encoder[TimeoutError] = deriveEncoder
}
