package com.mesosphere.cosmos

import com.mesosphere.http.MediaType
import com.mesosphere.util.ForwardedProtoHeader
import com.twitter.finagle.http.Fields
import io.finch.Error
import io.finch.items.HeaderItem

package object finch {
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

  def incompatibleScheme(
    value: String
  ): Error = {
    Error.NotValid(
      HeaderItem(ForwardedProtoHeader),
      s"Unable to determine scheme used to contact service: [$value]"
    )
  }
}
