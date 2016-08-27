package com.mesosphere.universe.v3.circe

import cats.data.Xor
import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.v3.model._
import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.{Return, Throw, Try}
import io.circe.generic.semiauto._
import io.circe.{Decoder, DecodingFailure, HCursor}

import scala.util.{Failure, Success}

object Decoders {

  implicit val decodeBundleDefinition: Decoder[BundleDefinition] = {
    Decoder.instance { (hc: HCursor) =>
      hc.downField("packagingVersion").as[PackagingVersion].flatMap {
        case V2PackagingVersion => hc.as[V2Bundle]
        case V3PackagingVersion => hc.as[V3Bundle]
      }
    }
  }
  implicit val decodeV2Bundle = deriveFor[V2Bundle].decoder
  implicit val decodeV3Bundle = deriveFor[V3Bundle].decoder

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
      iS <- cursor.downField("icon-small").as[Option[String]]
      iM <- cursor.downField("icon-medium").as[Option[String]]
      iL <- cursor.downField("icon-large").as[Option[String]]
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

  implicit val decodePackageDefinitionReleaseVersion: Decoder[PackageDefinition.ReleaseVersion] = {
    decodeViaTryConversion[Int, PackageDefinition.ReleaseVersion]
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

  implicit def decodeV3PackagingVersion[V <: PackagingVersion](implicit
    stringToV: Conversion[String, scala.util.Try[V]]
  ): Decoder[V] = {
    decodeViaTryConversion[String, V]
  }

  implicit val decodeV3V2Resource: Decoder[V2Resource] = deriveFor[V2Resource].decoder

  implicit val decodeV3V3Package: Decoder[V3Package] = deriveFor[V3Package].decoder

  implicit val decodeV3V3Resource: Decoder[V3Resource] = deriveFor[V3Resource].decoder

  private[this] def decodeViaTryConversion[A, B](implicit
    decodeA: Decoder[A],
    aToB: Conversion[A, scala.util.Try[B]]
  ): Decoder[B] = {
    decodeA.flatMap { a =>
      Decoder.instance { cursor =>
        a.as[scala.util.Try[B]] match {
          case Success(b) => Xor.Right(b)
          case Failure(e) => Xor.Left(DecodingFailure(e.getMessage, cursor.history))
        }
      }
    }
  }

}
