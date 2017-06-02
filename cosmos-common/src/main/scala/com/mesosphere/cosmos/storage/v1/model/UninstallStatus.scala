package com.mesosphere.cosmos.storage.v1.model

import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.cosmos.http.MediaType
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

sealed trait UninstallStatus
case object InProgress extends UninstallStatus
case class Failed(failures: List[String]) extends UninstallStatus

object UninstallStatus {
  val UninstallStatusMediaType: MediaType =
    MediaType.vndJson(List("dcos", "package"))("uninstall.status", 1)

  implicit val decoder: Decoder[UninstallStatus] = deriveDecoder
  implicit val encoder: Encoder[UninstallStatus] = deriveEncoder

  implicit val mediaTypedDecoder: MediaTypedDecoder[UninstallStatus] =
    MediaTypedDecoder(UninstallStatusMediaType)

  implicit val mediaTypedEncoder: MediaTypedEncoder[UninstallStatus] =
    MediaTypedEncoder(UninstallStatusMediaType)
}
