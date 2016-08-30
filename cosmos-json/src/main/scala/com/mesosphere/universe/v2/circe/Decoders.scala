package com.mesosphere.universe.v2.circe

import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.v2.model._
import io.circe.generic.semiauto._
import io.circe.{Decoder, HCursor, KeyDecoder}

object Decoders {
  implicit val decodeV2License: Decoder[License] = deriveDecoder[License]
  implicit val decodeV2PackageDetails: Decoder[PackageDetails] = deriveDecoder[PackageDetails]
  implicit val decodeV2Container: Decoder[Container] = deriveDecoder[Container]
  implicit val decodeV2Assets: Decoder[Assets] = deriveDecoder[Assets]
  implicit val decodeV2Images: Decoder[Images] = Decoder.instance { (cursor: HCursor) =>
    for {
      iS <- cursor.downField("icon-small").as[Option[String]]
      iM <- cursor.downField("icon-medium").as[Option[String]]
      iL <- cursor.downField("icon-large").as[Option[String]]
      ss <- cursor.downField("screenshots").as[Option[List[String]]]
    } yield Images(iS, iM, iL, ss)
  }
  implicit val decodeV2Resource: Decoder[Resource] = deriveDecoder[Resource]
  implicit val decodeV2PackageFiles: Decoder[PackageFiles] = deriveDecoder[PackageFiles]
  implicit val decodeV2CommandDefinition: Decoder[Command] = deriveDecoder[Command]
  implicit val decodeV2UniverseVersion: Decoder[UniverseVersion] = Decoder.decodeString.map(UniverseVersion)
  implicit val decodeV2PackagingVersion: Decoder[PackagingVersion] = Decoder.decodeString.map(PackagingVersion)
  implicit val decodeV2PackageRevision: Decoder[ReleaseVersion] = Decoder.decodeString.map(ReleaseVersion)
  implicit val decodeV2PackageDetailsVersion: Decoder[PackageDetailsVersion] = Decoder.decodeString.map(PackageDetailsVersion)
  implicit val keyDecodeV2PackageDetailsVersion: KeyDecoder[PackageDetailsVersion] = KeyDecoder.decodeKeyString.map(PackageDetailsVersion)
}
