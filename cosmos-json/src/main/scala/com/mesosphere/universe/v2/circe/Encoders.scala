package com.mesosphere.universe.v2.circe

import com.mesosphere.universe.common.circe.Encoders._
import com.mesosphere.universe.v2.model._
import io.circe.{Encoder, Json, JsonObject, ObjectEncoder}
import io.circe.generic.semiauto._
import io.circe.syntax._

object Encoders {

  implicit val encodeAssets: Encoder[Assets] = deriveFor[Assets].encoder
  implicit val encodeCommandDefinition: Encoder[Command] = deriveFor[Command].encoder
  implicit val encodeContainer: Encoder[Container] = deriveFor[Container].encoder
  implicit val encodeImages: Encoder[Images] = Encoder.instance { (images: Images) =>
    Json.obj(
      "icon-small" -> images.iconSmall.asJson,
      "icon-medium" -> images.iconMedium.asJson,
      "icon-large" -> images.iconLarge.asJson,
      "screenshots" -> images.screenshots.asJson
    )
  }
  implicit val encodeLicense: Encoder[License] = deriveFor[License].encoder
  implicit val encodePackageDetails: Encoder[PackageDetails] = deriveFor[PackageDetails].encoder
  implicit val encodePackageDetailsVersion: Encoder[PackageDetailsVersion] = Encoder.instance(_.toString.asJson)
  implicit val encodePackageFiles: Encoder[PackageFiles] = deriveFor[PackageFiles].encoder
  implicit val encodePackagingVersion: Encoder[PackagingVersion] = Encoder.instance(_.toString.asJson)
  implicit val encodePackageRevision: Encoder[ReleaseVersion] = Encoder.instance(_.toString.asJson)
  implicit val encodeResource: Encoder[Resource] = deriveFor[Resource].encoder

  implicit val encodeUniverseVersion: Encoder[UniverseVersion] = Encoder.instance(_.toString.asJson)

  def encodeIndexEntryFields(
    name: String,
    currentVersion: PackageDetailsVersion,
    versions: Map[PackageDetailsVersion, ReleaseVersion],
    description: String,
    framework: Boolean,
    tags: List[String],
    selected: Option[Boolean]
  ): Vector[(String, Json)] = {
    Vector(
      "name" -> name.asJson,
      "currentVersion" -> currentVersion.asJson,
      "versions" -> encodePackageDetailsVersionToReleaseVersionMap(versions),
      "description" -> description.asJson,
      "framework" -> framework.asJson,
      "tags" -> tags.asJson,
      "selected" -> selected.asJson
    )
  }

  def encodePackageDetailsVersionToReleaseVersionMap(versions: Map[PackageDetailsVersion, ReleaseVersion]): Json = {
    versions
      .map {
        case (PackageDetailsVersion(pdv), ReleaseVersion(rv)) => pdv -> rv
      }.asJson
  }

}
