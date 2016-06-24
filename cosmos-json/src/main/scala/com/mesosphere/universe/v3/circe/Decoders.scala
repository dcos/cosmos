package com.mesosphere.universe.v3.circe

import cats.data.Xor
import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.v3.model._
import com.twitter.util.{Return, Throw, Try}
import io.circe.generic.semiauto._
import io.circe.{Decoder, DecodingFailure, HCursor}

object Decoders {

  implicit val decodeArchitectures: Decoder[Architectures] = deriveFor[Architectures].decoder
  implicit val decodeAssets: Decoder[Assets] = deriveFor[Assets].decoder
  implicit val decodeBinary: Decoder[Binary] = deriveFor[Binary].decoder
  implicit val decodeCli: Decoder[Cli] = deriveFor[Cli].decoder
  implicit val decodeCommand: Decoder[Command] = deriveFor[Command].decoder
  implicit val decodeContainer: Decoder[Container] = deriveFor[Container].decoder
  implicit val decodeDcosReleaseVersion: Decoder[DcosReleaseVersion] = Decoder.decodeString.map { versionString =>
    DcosReleaseVersionParser.parseUnsafe(versionString)
  }
  implicit val decodeHashInfo: Decoder[HashInfo] = deriveFor[HashInfo].decoder
  implicit val decodeImages: Decoder[Images] = Decoder.instance { (cursor: HCursor) =>
    for {
      iS <- cursor.downField("icon-small").as[String]
      iM <- cursor.downField("icon-medium").as[String]
      iL <- cursor.downField("icon-large").as[String]
      ss <- cursor.downField("screenshots").as[Option[List[String]]]
    } yield Images(iS, iM, iL, ss)
  }
  implicit val decodeLicense: Decoder[License] = deriveFor[License].decoder
  implicit val decodeMarathon: Decoder[Marathon] = deriveFor[Marathon].decoder
  implicit val decodePlatforms: Decoder[Platforms] = deriveFor[Platforms].decoder

  implicit val decodePackageDefinitionVersion: Decoder[PackageDefinition.Version] = {
    Decoder.decodeString.map(PackageDefinition.Version)
  }
  implicit val decodePackageDefinitionTag: Decoder[PackageDefinition.Tag] =
    Decoder.instance[PackageDefinition.Tag] { (c: HCursor) =>
      Try { c.as[String].map(PackageDefinition.Tag(_)) } match {
        case Return(r) => r
        case Throw(ex) =>
          val msg = ex.getMessage.replaceAllLiterally("assertion failed: ", "")
          Xor.Left(new DecodingFailure(msg, c.history))
      }
    }
  implicit val decodePackageDefinitionReleaseVersion: Decoder[PackageDefinition.ReleaseVersion] =
    Decoder.instance[PackageDefinition.ReleaseVersion] { (c: HCursor) =>
      Try { c.as[Int].map(PackageDefinition.ReleaseVersion(_)) } match {
        case Return(r) => r
        case Throw(ex) =>
          val msg = ex.getMessage.replaceAllLiterally("assertion failed: ", "")
          Xor.Left(new DecodingFailure(msg, c.history))
      }
    }
  implicit val decodePackageDefinition: Decoder[PackageDefinition] = Decoder.instance[PackageDefinition] { (hc: HCursor) =>
    val packagingVersionCursor = hc.downField("packagingVersion")
    packagingVersionCursor.as[String].flatMap {
      case "2.0" => hc.as[V2Package]
      case "3.0" => hc.as[V3Package]
      case s => Xor.Left(new DecodingFailure(s"Supported packagingVersion: [2.0, 3.0] but was: '$s'", packagingVersionCursor.history))
    }
  }

  implicit val decodeRepository: Decoder[Repository] = deriveFor[Repository].decoder

  implicit val decodeV3V2Package: Decoder[V2Package] = deriveFor[V2Package].decoder
  implicit val decodeV3V2PackagingVersion: Decoder[V2PackagingVersion] = {
    Decoder.decodeString.map(V2PackagingVersion(_))
  }
  implicit val decodeV3V2Resource: Decoder[V2Resource] = deriveFor[V2Resource].decoder

  implicit val decodeV3V3Package: Decoder[V3Package] = deriveFor[V3Package].decoder
  implicit val decodeV3V3PackagingVersion: Decoder[V3PackagingVersion] = {
    Decoder.decodeString.map(V3PackagingVersion(_))
  }
  implicit val decodeV3V3Resource: Decoder[V3Resource] = deriveFor[V3Resource].decoder

}
