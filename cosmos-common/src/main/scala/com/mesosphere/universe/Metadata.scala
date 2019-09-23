package com.mesosphere.universe

import com.mesosphere.universe
import com.mesosphere.cosmos.circe.Decoders._
import io.circe.JsonObject
import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.DecodingFailure
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps

package v3.model {

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
    config: Option[JsonObject] = None,
    lastUpdated: Option[Long] = None,
    knownIssues: Option[Boolean] = None
  ) extends universe.v4.model.Metadata

  object V3Metadata {
    implicit val decodeV3Metadata: Decoder[universe.v3.model.V3Metadata] = deriveDecoder[universe.v3.model.V3Metadata]
    implicit val encodeV3Metadata: Encoder[universe.v3.model.V3Metadata] = deriveEncoder[universe.v3.model.V3Metadata]
  }

}

package v4.model {

  sealed trait Metadata

  object Metadata {
    implicit val decodeMetadata: Decoder[universe.v4.model.Metadata] = {
      Decoder.instance[universe.v4.model.Metadata] { (hc: HCursor) =>
        hc.downField("packagingVersion").as[universe.v4.model.PackagingVersion].flatMap {
          case universe.v5.model.V5PackagingVersion => hc.as[universe.v5.model.V5Metadata]
          case universe.v4.model.V4PackagingVersion => hc.as[universe.v4.model.V4Metadata]
          case universe.v3.model.V3PackagingVersion => hc.as[universe.v3.model.V3Metadata]
          case universe.v3.model.V2PackagingVersion => Left(DecodingFailure(
            "V2Metadata is not supported",
            hc.history
          ))
        }
      }
    }
    implicit val encodeMetadata: Encoder[universe.v4.model.Metadata] = Encoder.instance {
      case v3Metadata: universe.v3.model.V3Metadata => v3Metadata.asJson
      case v4Metadata: universe.v4.model.V4Metadata => v4Metadata.asJson
      case v5Metadata: universe.v5.model.V5Metadata => v5Metadata.asJson
    }
  }

  case class V4Metadata(
    packagingVersion: V4PackagingVersion.type = V4PackagingVersion,
    name: String,
    version: universe.v3.model.Version,
    maintainer: String,
    description: String,
    tags: List[universe.v3.model.Tag] = Nil,
    scm: Option[String] = None,
    website: Option[String] = None,
    framework: Option[Boolean] = None,
    preInstallNotes: Option[String] = None,
    postInstallNotes: Option[String] = None,
    postUninstallNotes: Option[String] = None,
    licenses: Option[List[universe.v3.model.License]] = None,
    minDcosReleaseVersion: Option[universe.v3.model.DcosReleaseVersion] = None,
    marathon: Option[universe.v3.model.Marathon] = None,
    resource: Option[universe.v3.model.V3Resource] = None,
    config: Option[JsonObject] = None,
    upgradesFrom: Option[List[universe.v3.model.VersionSpecification]] = None,
    downgradesTo: Option[List[universe.v3.model.VersionSpecification]] = None,
    lastUpdated: Option[Long] = None,
    knownIssues: Option[Boolean] = None
  ) extends universe.v4.model.Metadata

  object V4Metadata {
    implicit val decodeV4Metadata: Decoder[universe.v4.model.V4Metadata] = deriveDecoder[universe.v4.model.V4Metadata]
    implicit val encodeV4Metadata: Encoder[universe.v4.model.V4Metadata] = deriveEncoder[universe.v4.model.V4Metadata]
  }
}

package v5.model {

  case class V5Metadata(
     packagingVersion: V5PackagingVersion.type = V5PackagingVersion,
     name: String,
     version: universe.v3.model.Version,
     maintainer: String,
     description: String,
     tags: List[universe.v3.model.Tag] = Nil,
     scm: Option[String] = None,
     website: Option[String] = None,
     framework: Option[Boolean] = None,
     preInstallNotes: Option[String] = None,
     postInstallNotes: Option[String] = None,
     postUninstallNotes: Option[String] = None,
     licenses: Option[List[universe.v3.model.License]] = None,
     minDcosReleaseVersion: Option[universe.v3.model.DcosReleaseVersion] = None,
     marathon: Option[universe.v3.model.Marathon] = None,
     resource: Option[universe.v3.model.V3Resource] = None,
     config: Option[JsonObject] = None,
     upgradesFrom: Option[List[universe.v3.model.VersionSpecification]] = None,
     downgradesTo: Option[List[universe.v3.model.VersionSpecification]] = None,
     manager: Option[universe.v5.model.Manager],
     lastUpdated: Option[Long] = None,
     knownIssues: Option[Boolean] = None
   ) extends universe.v4.model.Metadata

  object V5Metadata {
    implicit val decodeV5Metadata: Decoder[universe.v5.model.V5Metadata] = deriveDecoder[universe.v5.model.V5Metadata]
    implicit val encodeV5Metadata: Encoder[universe.v5.model.V5Metadata] = deriveEncoder[universe.v5.model.V5Metadata]
  }
}
