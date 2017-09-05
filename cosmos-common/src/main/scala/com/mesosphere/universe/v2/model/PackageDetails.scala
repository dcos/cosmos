package com.mesosphere.universe.v2.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-2.x/repo/meta/schema/package-schema.json
  */
case class PackageDetails(
  packagingVersion: PackagingVersion,
  name: String,
  version: PackageDetailsVersion,
  maintainer: String,
  description: String,
  tags: List[String] = Nil,
  selected: Option[Boolean] = None,
  scm: Option[String] = None,
  website: Option[String] = None,
  framework: Option[Boolean] = None,
  preInstallNotes: Option[String] = None,
  postInstallNotes: Option[String] = None,
  postUninstallNotes: Option[String] = None,
  licenses: Option[List[License]] = None
)

object PackageDetails {
  implicit val encodeV2PackageDetails: Encoder[PackageDetails] = deriveEncoder[PackageDetails]
  implicit val decodeV2PackageDetails: Decoder[PackageDetails] = deriveDecoder[PackageDetails]
}
