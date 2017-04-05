package com.mesosphere.universe

import cats.syntax.either._
import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.HCursor
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps

package v3.model {

  sealed abstract class PackageDefinition

  object PackageDefinition {

    implicit val packageDefinitionOrdering = new Ordering[PackageDefinition] {
      override def compare(a: PackageDefinition, b: PackageDefinition): Int = {
        PackageDefinition.compare(
          (a.name, a.version, a.releaseVersion),
          (b.name, b.version, b.releaseVersion)
        )
      }
    }

    def compare(
      a: (String, Version, ReleaseVersion),
      b: (String, Version, ReleaseVersion)
    ): Int = {
      val (aName, _, aReleaseVersion) = a
      val (bName, _, bReleaseVersion) = b

      val orderName = aName.compare(bName)
      if (orderName != 0) {
        orderName
      } else {
        // Use release version
        aReleaseVersion.value.compare(bReleaseVersion.value)
      }
    }

    implicit val decodePackageDefinition: Decoder[PackageDefinition] = {
      Decoder.instance[PackageDefinition] { (hc: HCursor) =>
        hc.downField("packagingVersion").as[PackagingVersion].flatMap {
          case V2PackagingVersion => hc.as[V2Package]
          case V3PackagingVersion => hc.as[V3Package]
        }
      }
    }

    implicit val encodePackageDefinition: Encoder[PackageDefinition] = Encoder.instance {
      case v2: V2Package => v2.asJson
      case v3: V3Package => v3.asJson
    }
  }

  sealed trait SupportedPackageDefinition
    extends PackageDefinition

  object SupportedPackageDefinition {

    implicit val supportedPackageDefinitionOrdering: Ordering[SupportedPackageDefinition] =
      PackageDefinition.packageDefinitionOrdering.on(identity)

    implicit val decodeSupportedPackageDefinition: Decoder[SupportedPackageDefinition] = {
      Decoder.instance[SupportedPackageDefinition] { (hc: HCursor) =>
        hc.downField("packagingVersion").as[PackagingVersion].flatMap {
          case V3PackagingVersion => hc.as[V3Package]
          case V2PackagingVersion => Left(DecodingFailure(
            s"V2Package is not a supported package definition",
            hc.history
          ))
        }
      }
    }

    implicit val encodeSupportedPackageDefinition: Encoder[SupportedPackageDefinition] = Encoder.instance {
      case v3: V3Package => v3.asJson
    }

  }

  /**
    * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/v20Package
    */
  case class V2Package(
    packagingVersion: V2PackagingVersion.type = V2PackagingVersion,
    name: String,
    version: Version,
    releaseVersion: ReleaseVersion,
    maintainer: String,
    description: String,
    marathon: Marathon,
    tags: List[Tag] = Nil,
    selected: Option[Boolean] = None,
    scm: Option[String] = None,
    website: Option[String] = None,
    framework: Option[Boolean] = None,
    preInstallNotes: Option[String] = None,
    postInstallNotes: Option[String] = None,
    postUninstallNotes: Option[String] = None,
    licenses: Option[List[License]] = None,
    resource: Option[V2Resource] = None,
    config: Option[JsonObject] = None,
    command: Option[Command] = None
  ) extends PackageDefinition with Ordered[V2Package] {
    override def compare(that: V2Package): Int = {
      PackageDefinition.compare(
        (name, version, releaseVersion),
        (that.name, that.version, that.releaseVersion)
      )
    }
  }

  object V2Package {
    implicit val decodeV3V2Package: Decoder[V2Package] = deriveDecoder[V2Package]
    implicit val encodeV2Package: Encoder[V2Package] = deriveEncoder[V2Package]
  }

  /**
    * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/v30Package
    */
  case class V3Package(
    packagingVersion: V3PackagingVersion.type = V3PackagingVersion,
    name: String,
    version: Version,
    releaseVersion: ReleaseVersion,
    maintainer: String,
    description: String,
    tags: List[Tag] = Nil,
    selected: Option[Boolean] = None,
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
    command: Option[Command] = None
  ) extends SupportedPackageDefinition with Ordered[V3Package] {
    override def compare(that: V3Package): Int = {
      PackageDefinition.compare(
        (name, version, releaseVersion),
        (that.name, that.version, that.releaseVersion)
      )
    }
  }

  object V3Package {
    implicit val decodeV3V3Package: Decoder[V3Package] = deriveDecoder[V3Package]
    implicit val encodeV3Package: Encoder[V3Package] = deriveEncoder[V3Package]
  }

}
