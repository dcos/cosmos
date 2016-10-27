package com.mesosphere.cosmos.finch

import com.twitter.finagle.http.Status
import io.circe.JsonObject

abstract class RequestError(causedBy: Option[Throwable] = None) extends RuntimeException(causedBy.orNull) {
  def status: Status
  def errType: String
  def getData: Option[JsonObject]
  def getHeaders: Map[String, String]
}
