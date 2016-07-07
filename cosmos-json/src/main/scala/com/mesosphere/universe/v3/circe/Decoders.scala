package com.mesosphere.universe.v3.circe

import cats.data.Xor
import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.v3.model._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.{Return, Throw, Try}
import io.circe.generic.semiauto._
import io.circe.{Decoder, DecodingFailure, HCursor}

import scala.util.{Failure, Success}

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
  implicit val decodePackageDefinition: Decoder[PackageDefinition] = {
    Decoder.instance[PackageDefinition] { (hc: HCursor) =>
      hc.downField("packagingVersion").as[PackagingVersion].flatMap {
        case V2PackagingVersion => hc.as[V2Package]
        case V3PackagingVersion => hc.as[V3Package]
      }
    }
  }

  implicit val decodeRepository: Decoder[Repository] = deriveFor[Repository].decoder

  implicit val decodeV3V2Package: Decoder[V2Package] = deriveFor[V2Package].decoder

  implicit val decodeString: Decoder[String] = {
    Decoder.decodeString.withErrorMessage("String value expected")
  }

  implicit val decodeV3V2PackagingVersion: Decoder[V2PackagingVersion.type] = {
    Decoder[String].flatMapConversion(version => version.as[scala.util.Try[V2PackagingVersion.type]])
  }

  implicit val decodeV3V3PackagingVersion: Decoder[V3PackagingVersion.type] = {
    Decoder[String].flatMapConversion(version => version.as[scala.util.Try[V3PackagingVersion.type]])
  }

  implicit val decodeV3PackagingVersion: Decoder[PackagingVersion] = {
    Decoder[String].flatMapConversion(version => version.as[scala.util.Try[PackagingVersion]])
  }

  implicit val decodeV3V2Resource: Decoder[V2Resource] = deriveFor[V2Resource].decoder

  implicit val decodeV3V3Package: Decoder[V3Package] = deriveFor[V3Package].decoder

  implicit val decodeV3V3Resource: Decoder[V3Resource] = deriveFor[V3Resource].decoder

  implicit final class DecoderOps[A](val decoder: Decoder[A]) extends AnyVal {

    def flatMapConversion[B](f: A => scala.util.Try[B]): Decoder[B] = {
      decoder.flatMap { value =>
        Decoder.instance { cursor =>
          f(value) match {
            case Success(b) => Xor.Right(b)
            case Failure(e) => Xor.Left(DecodingFailure(e.getMessage, cursor.history))
          }
        }
      }
    }

  }

}
