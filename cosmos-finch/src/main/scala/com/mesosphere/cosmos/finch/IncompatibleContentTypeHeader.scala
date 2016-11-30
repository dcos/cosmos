package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.MediaType
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Status
import io.circe.JsonObject
import io.circe.syntax._

case class IncompatibleContentTypeHeader(available: Set[MediaType], specified: MediaType)
  extends RequestError {
  val errType: String = "not_valid"
  val status: Status = Status.BadRequest

  val getData: Option[JsonObject] = Some(JsonObject.fromMap(Map(
    "invalidItem" -> JsonObject.fromMap(Map(
      "type" -> "header".asJson,
      "name" -> Fields.ContentType.asJson
    )).asJson,
    "specified" -> specified.show.asJson,
    "available" -> available.map(_.show).asJson
  )))

  val getHeaders: Map[String, String] = Map.empty

  override def getMessage: String = {
    val validChoices = available.map(_.show).mkString(", ")
    s"${Fields.ContentType} header was ${specified.show}, but should be one of: $validChoices"
  }

}
