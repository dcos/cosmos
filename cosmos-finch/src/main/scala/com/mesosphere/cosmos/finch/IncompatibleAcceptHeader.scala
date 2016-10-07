package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.MediaType
import com.twitter.finagle.http.Status
import io.circe.JsonObject
import io.circe.syntax._

case class IncompatibleAcceptHeader(available: Set[MediaType], specified: Set[MediaType]) extends RequestError {
  val errType: String = "not_valid"
  val status: Status = Status.BadRequest

  val getData: Option[JsonObject] = Some(JsonObject.fromMap(Map(
    "invalidItem" -> JsonObject.fromMap(Map(
      "type" -> "header".asJson,
      "name" -> "Accept".asJson
    )).asJson,
    "specified" -> specified.map(_.show).asJson,
    "available" -> available.map(_.show).asJson
  )))

  val getHeaders: Map[String, String] = Map.empty
}
