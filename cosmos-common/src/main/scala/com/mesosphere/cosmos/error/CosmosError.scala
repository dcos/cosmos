package com.mesosphere.cosmos.error

import io.circe.Encoder
import io.circe.JsonObject
import io.netty.handler.codec.http.HttpResponseStatus

trait CosmosError{
  def message: String
  def data: Option[JsonObject]
  def status: HttpResponseStatus = HttpResponseStatus.BAD_REQUEST

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
