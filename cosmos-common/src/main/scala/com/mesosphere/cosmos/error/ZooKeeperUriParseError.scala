package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

case class ZooKeeperUriParseError(s: String) extends CosmosError {
  override def message: String = s"ZooKeeper URI not parsable: $s"
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
}

object ZooKeeperUriParseError {
  implicit val encoder: Encoder[ZooKeeperUriParseError] = deriveEncoder
}
