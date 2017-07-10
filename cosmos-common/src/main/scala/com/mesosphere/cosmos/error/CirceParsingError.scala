package com.mesosphere.cosmos.error

import io.circe.Json
import io.circe.JsonObject

final case class CirceParsingError(
    circeError: io.circe.Error,
    jsonData: Option[JsonObject]
  ) extends CosmosError {
  override def data: Option[JsonObject] = jsonData
  override def message: String = s"${
    if (jsonData == None) circeError.getMessage
    else {
      val Right(line) = Json.fromJsonObject(data.get).cursor.get[Int]("line")
      "Unable to parse the json at line " + line
    }
  }"
}
