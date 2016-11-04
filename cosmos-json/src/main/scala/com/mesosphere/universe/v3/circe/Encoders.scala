package com.mesosphere.universe.v3.circe

import com.mesosphere.universe.common.circe.Encoders._
import com.mesosphere.universe.v3.model._
import io.circe.generic.semiauto._
import io.circe.{Encoder, Json}
import io.circe.syntax._

object Encoders {

  implicit val encodeArchitectures: Encoder[Architectures] = deriveEncoder[Architectures]
  implicit val encodeAssets: Encoder[Assets] = deriveEncoder[Assets]
  implicit val encodeBinary: Encoder[Binary] = deriveEncoder[Binary]
  implicit val encodeCli: Encoder[Cli] = deriveEncoder[Cli]
  implicit val encodeCommand: Encoder[Command] = deriveEncoder[Command]
  implicit val encodeContainer: Encoder[Container] = deriveEncoder[Container]
  implicit val encodeDcosReleaseVersion: Encoder[DcosReleaseVersion] = Encoder.instance(_.show.asJson)
  implicit val encodeHashInfo: Encoder[HashInfo] = deriveEncoder[HashInfo]
  implicit val encodeImages: Encoder[Images] = Encoder.instance { (images: Images) =>
    Json.obj(
      "icon-small" -> images.iconSmall.asJson,
      "icon-medium" -> images.iconMedium.asJson,
      "icon-large" -> images.iconLarge.asJson,
      "screenshots" -> images.screenshots.asJson
    )
  }
  implicit val encodeLicense: Encoder[License] = deriveEncoder[License]
  implicit val encodeMarathon: Encoder[Marathon] = deriveEncoder[Marathon]
  implicit val encodePackageDefinition: Encoder[PackageDefinition] = Encoder.instance {
    case v2: V2Package => v2.asJson
    case v3: V3Package => v3.asJson
  }

  implicit val encodePlatforms: Encoder[Platforms] = deriveEncoder[Platforms]

  implicit val encodePackageDefinitionVersion: Encoder[PackageDefinition.Version] = {
    Encoder.instance(_.toString.asJson)
  }
  implicit val encodePackageDefinitionTag: Encoder[PackageDefinition.Tag] = {
    Encoder.instance(_.value.asJson)
  }
  implicit val encodePackageDefinitionReleaseVersion: Encoder[PackageDefinition.ReleaseVersion] = {
    Encoder.instance(_.value.asJson)
  }
  implicit val encodeRepository: Encoder[Repository] = deriveEncoder[Repository]
  implicit val encodeV2Package: Encoder[V2Package] = deriveEncoder[V2Package]

  implicit def encodePackagingVersion[A <: PackagingVersion]: Encoder[A] = {
    Encoder[String].contramap(version => version.show)
  }

  implicit val encodeV2Resource: Encoder[V2Resource] = deriveEncoder[V2Resource]
  implicit val encodeV3Package: Encoder[V3Package] = deriveEncoder[V3Package]
  implicit val encodeV3Resource: Encoder[V3Resource] = deriveEncoder[V3Resource]

  implicit val encodeMetadata = deriveEncoder[Metadata]
}
