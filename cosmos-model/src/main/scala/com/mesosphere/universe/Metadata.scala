package com.mesosphere.universe

import cats.syntax.either._
import com.mesosphere.universe.common.circe.Decoders._
import io.circe.JsonObject
import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.DecodingFailure
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps

package v3.model {

  sealed trait Metadata

  object Metadata {
    implicit val decodeMetadata: Decoder[Metadata] = {
      Decoder.instance[Metadata] { (hc: HCursor) =>
        hc.downField("packagingVersion").as[PackagingVersion].flatMap {
          case V3PackagingVersion => hc.as[V3Metadata]
          case V2PackagingVersion => Left(DecodingFailure(
            "V2Metadata is not supported",
            hc.history
          ))
        }
      }
    }
    implicit val encodeMetadata: Encoder[Metadata] = Encoder.instance {
      case v3Metadata: V3Metadata => v3Metadata.asJson
    }
  }

  case class V3Metadata(
    packagingVersion: V3PackagingVersion.type = V3PackagingVersion,
    name: String,
    version: Version,
    maintainer: String,
    description: String,
    tags: List[Tag] = Nil,
    scm: Option[String] = None,
    website: Option[String] = None,
    framework: Option[Boolean] = None,
    preInstallNotes: Option[String] = None,
    postInstallNotes: Option[String] = None,
    postUninstallNotes: Option[String] = None,
    licenses: Option[List[License]] = None,
    minDcosReleaseVersion: Option[DcosReleaseVersion] = None,
    marathon: Option[Marathon] = None,
    resource: Option[V3Resource] = None,
    config: Option[JsonObject] = None
  ) extends Metadata

  object V3Metadata {
    implicit val decodeV3Metadata: Decoder[V3Metadata] = deriveDecoder[V3Metadata]
    implicit val encodeV3Metadata: Encoder[V3Metadata] = deriveEncoder[V3Metadata]
  }

}
