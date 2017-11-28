package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.rpc
import io.circe.Encoder
import io.circe.JsonObject

trait CosmosError {
  def message: String
  def data: Option[JsonObject]

  def exception: CosmosException = {
    CosmosException(this)
  }

  def errorResponse: rpc.v1.model.ErrorResponse = {
    rpc.v1.model.ErrorResponse(
      getClass.getSimpleName,
      message,
      data
    )
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
