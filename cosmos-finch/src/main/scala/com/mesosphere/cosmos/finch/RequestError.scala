package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.MediaType
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Status
import io.circe.JsonObject
import io.finch.Error
import io.finch.items.HeaderItem

object RequestError {
  def incompatibleContentTypeHeader(
    available: Set[MediaType],
    specified: MediaType
  ): Error = {
    val validChoices = available.map(_.show).mkString(", ")

    Error.NotValid(
      HeaderItem(Fields.ContentType),
      s"Media type was ${specified.show}, but should be one of [$validChoices]"
    )
  }

  def incompatibleAcceptHeader(
    available: Set[MediaType],
    specified: Set[MediaType]
  ): Error = {
    val specifiedStr = specified.map(_.show).mkString(", ")
    val availableStr = available.map(_.show).mkString(", ")

    Error.NotValid(
      HeaderItem(Fields.Accept),
      s"Media type was [$specifiedStr] but should be one of [$availableStr]"
    )
  }
}
