package com.mesosphere.universe.v2.circe

import com.mesosphere.universe.common.circe.Encoders._
import com.mesosphere.universe.v2.model._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

object Encoders {

  implicit val encodeAssets: Encoder[Assets] = deriveEncoder[Assets]
  implicit val encodeCommandDefinition: Encoder[Command] = deriveEncoder[Command]
  implicit val encodeContainer: Encoder[Container] = deriveEncoder[Container]
  implicit val encodeImages: Encoder[Images] = Encoder.instance { (images: Images) =>
    Json.obj(
      "icon-small" -> images.iconSmall.asJson,
      "icon-medium" -> images.iconMedium.asJson,
      "icon-large" -> images.iconLarge.asJson,
      "screenshots" -> images.screenshots.asJson
    )
  }
  implicit val encodeLicense: Encoder[License] = deriveEncoder[License]
  implicit val encodePackageDetails: Encoder[PackageDetails] = deriveEncoder[PackageDetails]
  implicit val encodePackageDetailsVersion: Encoder[PackageDetailsVersion] = Encoder.instance(_.toString.asJson)
  implicit val encodePackageFiles: Encoder[PackageFiles] = deriveEncoder[PackageFiles]
  implicit val encodePackagingVersion: Encoder[PackagingVersion] = Encoder.instance(_.toString.asJson)
  implicit val encodePackageRevision: Encoder[ReleaseVersion] = Encoder.instance(_.toString.asJson)
  implicit val encodeResource: Encoder[Resource] = deriveEncoder[Resource]

  implicit val encodeUniverseVersion: Encoder[UniverseVersion] = Encoder.instance(_.toString.asJson)

  implicit val keyEncodeV2PackageDetailsVersion: KeyEncoder[PackageDetailsVersion] = KeyEncoder.instance(_.toString)

}
