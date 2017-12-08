package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import com.twitter.finagle.http.Status

trait CosmosError {
  def message: String
  def data: Option[JsonObject]
  def status: Status = Status.BadRequest

  def exception: CosmosException = {
    CosmosException(this)
  }
}

object CosmosError {
  def deriveData[T <: CosmosError](
    error: T
  )(
    implicit encoder: Encoder[T]
  ): Option[JsonObject] = {
    encoder(error).asObject
  }
}
