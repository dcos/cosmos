package com.mesosphere.universe.v3.circe

import cats.data.Xor
import com.mesosphere.universe
import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.v3.model._
import com.twitter.util.{Return, Throw, Try}
import io.circe.generic.semiauto._
import io.circe.{Decoder, DecodingFailure, HCursor}

import scala.util.{Failure, Success}

object Decoders {

  implicit val decodeArchitectures: Decoder[Architectures] = deriveDecoder[Architectures]
  implicit val decodeAssets: Decoder[Assets] = deriveDecoder[Assets]
  implicit val decodeBinary: Decoder[Binary] = deriveDecoder[Binary]
  implicit val decodeCli: Decoder[Cli] = deriveDecoder[Cli]
  implicit val decodeCommand: Decoder[Command] = deriveDecoder[Command]
  implicit val decodeContainer: Decoder[Container] = deriveDecoder[Container]
  implicit val decodeDcosReleaseVersion: Decoder[DcosReleaseVersion] = Decoder.decodeString.map { versionString =>
    DcosReleaseVersionParser.parseUnsafe(versionString)
  }
  implicit val decodeHashInfo: Decoder[HashInfo] = deriveDecoder[HashInfo]
  implicit val decodeImages: Decoder[Images] = Decoder.instance { (cursor: HCursor) =>
    for {
      iS <- cursor.downField("icon-small").as[Option[String]]
      iM <- cursor.downField("icon-medium").as[Option[String]]
      iL <- cursor.downField("icon-large").as[Option[String]]
      ss <- cursor.downField("screenshots").as[Option[List[String]]]
    } yield Images(iS, iM, iL, ss)
  }
  implicit val decodeLicense: Decoder[License] = deriveDecoder[License]
  implicit val decodeMarathon: Decoder[Marathon] = deriveDecoder[Marathon]
  implicit val decodePlatforms: Decoder[Platforms] = deriveDecoder[Platforms]

  implicit val decodePackageDefinitionVersion: Decoder[PackageDefinition.Version] = {
    Decoder.decodeString.map(PackageDefinition.Version)
  }
  implicit val decodePackageDefinitionTag: Decoder[PackageDefinition.Tag] =
    Decoder.instance[PackageDefinition.Tag] { (c: HCursor) =>
      Try { c.as[String].map(PackageDefinition.Tag(_)) } match {
        case Return(r) => r
        case Throw(ex) =>
          val msg = ex.getMessage.replaceAllLiterally("assertion failed: ", "")
          Xor.Left(DecodingFailure(msg, c.history))
      }
    }

  implicit val decodePackageDefinitionReleaseVersion: Decoder[PackageDefinition.ReleaseVersion] =
    Decoder.instance[PackageDefinition.ReleaseVersion] { (c: HCursor) =>
      c.as[Int].map(PackageDefinition.ReleaseVersion(_)).flatMap {
        case Return(v) => Xor.Right(v)
        case Throw(e) => Xor.Left(DecodingFailure(e.getMessage, c.history))
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

  implicit val decodeRepository: Decoder[Repository] = deriveDecoder[Repository]

  implicit val decodeV3V2Package: Decoder[V2Package] = deriveDecoder[V2Package]

  implicit val decodeString: Decoder[String] = {
    Decoder.decodeString.withErrorMessage("String value expected")
  }

  implicit val decodeV3V2Resource: Decoder[V2Resource] = deriveDecoder[V2Resource]

  implicit val decodeV3V3Package: Decoder[V3Package] = deriveDecoder[V3Package]

  implicit val decodeV3V3Resource: Decoder[V3Resource] = deriveDecoder[V3Resource]

  implicit val decodeBundleDefinition: Decoder[BundleDefinition] = {
    Decoder.instance { (hc: HCursor) =>
      hc.downField("packagingVersion").as[PackagingVersion].flatMap {
        case V2PackagingVersion => hc.as[V2Bundle]
        case V3PackagingVersion => hc.as[V3Bundle]
      }
    }
  }
  implicit val decodeV2Bundle = deriveDecoder[V2Bundle]
  implicit val decodeV3Bundle = deriveDecoder[V3Bundle]

  implicit val decodeV3PackagingVersion: Decoder[PackagingVersion] = Decoder.instance[PackagingVersion] { (c: HCursor) =>
    c.as[String].map(PackagingVersion(_)).flatMap {
      case Return(v) => Xor.Right(v)
      case Throw(e) => Xor.Left(DecodingFailure(e.getMessage, c.history))
    }
  }

  implicit val decodeV3V2PackagingVersion: Decoder[V2PackagingVersion.type] = packagingVersionSubclassToString(V2PackagingVersion)
  implicit val decodeV3V3PackagingVersion: Decoder[V3PackagingVersion.type] = packagingVersionSubclassToString(V3PackagingVersion)

  private[this] def packagingVersionSubclassToString[V <: PackagingVersion](expected: V): Decoder[V] =
    Decoder.instance { (c: HCursor) =>
      c.as[String].flatMap {
        case expected.show => Xor.Right(expected)
        case x => Xor.Left(DecodingFailure(
          s"Expected value [${expected.show}] for packaging version, but found [$x]",
          c.history
        ))
      }
    }

}
