package com.mesosphere.cosmos.label.v1.model

import com.mesosphere.universe
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._

/** Copy of [[com.mesosphere.universe.v2.model.PackageDetails]] with additional field `images` from
  * [[com.mesosphere.universe.v2.model.Resource]].
  *
  * For the properties `selected` and `framework` we default them to Some(false). This is because
  * we need to be able to deserialize JSONs that already exists as label in Marathon that may not
  * include this property but we want to default them to false when they don't exist.
  */
case class PackageMetadata(
  packagingVersion: universe.v2.model.PackagingVersion,
  name: String,
  version: universe.v2.model.PackageDetailsVersion,
  maintainer: String,
  description: String,
  tags: List[String] = Nil,
  selected: Option[Boolean] = Some(false),
  scm: Option[String] = None,
  website: Option[String] = None,
  framework: Option[Boolean] = Some(false),
  preInstallNotes: Option[String] = None,
  postInstallNotes: Option[String] = None,
  postUninstallNotes: Option[String] = None,
  licenses: Option[List[universe.v2.model.License]] = None,
  images: Option[universe.v2.model.Images] = None
)

object PackageMetadata {

  implicit val encoder: Encoder[PackageMetadata] = deriveEncoder[PackageMetadata]
  implicit val decoder: Decoder[PackageMetadata] = deriveDecoder[PackageMetadata]
}
