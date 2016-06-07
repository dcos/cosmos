package com.mesosphere.universe.v2.circe

import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.v2.model._
import io.circe.generic.semiauto._
import io.circe.{Decoder, HCursor}

object Decoders {
  implicit val decodeLicense: Decoder[License] = deriveFor[License].decoder
  implicit val decodePackageDefinition: Decoder[PackageDetails] = deriveFor[PackageDetails].decoder
  implicit val decodeContainer: Decoder[Container] = deriveFor[Container].decoder
  implicit val decodeAssets: Decoder[Assets] = deriveFor[Assets].decoder
  implicit val decodeImages: Decoder[Images] = Decoder.instance { (cursor: HCursor) =>
    for {
      iS <- cursor.downField("icon-small").as[String]
      iM <- cursor.downField("icon-medium").as[String]
      iL <- cursor.downField("icon-large").as[String]
      ss <- cursor.downField("screenshots").as[Option[List[String]]]
    } yield Images(iS, iM, iL, ss)
  }
  implicit val decodeResource: Decoder[Resource] = deriveFor[Resource].decoder
  implicit val decodePackageIndex: Decoder[UniverseIndexEntry] = Decoder.instance { (cursor: HCursor) =>
    for {
      n <- cursor.downField("name").as[String]
      c <- cursor.downField("currentVersion").as[PackageDetailsVersion]
      v <- cursor.downField("versions").as[Map[String, String]]
      d <- cursor.downField("description").as[String]
      f <- cursor.downField("framework").as[Boolean]
      t <- cursor.downField("tags").as[List[String]]
      p <- cursor.downField("selected").as[Option[Boolean]]
    } yield {
      val versions = v.map { case (s1, s2) =>
        PackageDetailsVersion(s1) -> ReleaseVersion(s2)
      }
      UniverseIndexEntry(n, c, versions, d, f, t, p)
    }
  }
  implicit val decodeUniverseIndex: Decoder[UniverseIndex] = deriveFor[UniverseIndex].decoder
  implicit val decodePackageFiles: Decoder[PackageFiles] = deriveFor[PackageFiles].decoder
  implicit val decodeCommandDefinition: Decoder[Command] = deriveFor[Command].decoder
  implicit val decodeUniverseVersion: Decoder[UniverseVersion] = Decoder.decodeString.map(UniverseVersion)
  implicit val decodePackagingVersion: Decoder[PackagingVersion] = Decoder.decodeString.map(PackagingVersion)
  implicit val decodePackageRevision: Decoder[ReleaseVersion] = Decoder.decodeString.map(ReleaseVersion)
  implicit val decodePackageDetailsVersion: Decoder[PackageDetailsVersion] = Decoder.decodeString.map(PackageDetailsVersion)
}
