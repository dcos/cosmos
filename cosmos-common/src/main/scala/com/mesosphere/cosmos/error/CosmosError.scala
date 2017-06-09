package com.mesosphere.cosmos.error

import com.twitter.finagle.http.Status
import io.circe.Encoder
import io.circe.JsonObject

trait CosmosError {
  def message: String
  def data: Option[JsonObject]

  def exception: CosmosException = {
    CosmosException(this)
  }

  final def exception(
    status: Status,
    headers: Map[String, String],
    causedBy: Option[Throwable]
  ): CosmosException = {
    CosmosException(this, status, headers, causedBy)
  }
}

object CosmosError {
  def deriveData[T <: CosmosError](error: T)(implicit encoder: Encoder[T]): Option[JsonObject] = {
    encoder(error).asObject
  }
}
