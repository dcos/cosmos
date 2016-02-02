package com.mesosphere.cosmos.circe

import com.mesosphere.cosmos.ErrorResponse
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.model.mesos.master._
import com.netaporter.uri.Uri
import io.circe.generic.semiauto._
import io.circe.{Decoder, HCursor}

object Decoders {
  implicit val decodeLicense: Decoder[License] = deriveFor[License].decoder
  implicit val decodePackageDefinition: Decoder[PackageDefinition] = deriveFor[PackageDefinition].decoder
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
  implicit val decodePackageIndex: Decoder[UniverseIndexEntry] = deriveFor[UniverseIndexEntry].decoder
  implicit val decodeUniverseIndex: Decoder[UniverseIndex] = deriveFor[UniverseIndex].decoder
  implicit val decodePackageInfo: Decoder[PackageInfo] = deriveFor[PackageInfo].decoder
  implicit val decodeMasterState: Decoder[MasterState] = deriveFor[MasterState].decoder
  implicit val decodeFramework: Decoder[Framework] = deriveFor[Framework].decoder
  implicit val decodeMesosFrameworkTearDownResponse: Decoder[MesosFrameworkTearDownResponse] = deriveFor[MesosFrameworkTearDownResponse].decoder
  implicit val decodeMarathonAppResponse: Decoder[MarathonAppResponse] = deriveFor[MarathonAppResponse].decoder
  implicit val decodeMarathonAppsResponse: Decoder[MarathonAppsResponse] = deriveFor[MarathonAppsResponse].decoder
  implicit val decoder: Decoder[AppId] = Decoder.decodeString.map(AppId(_))
  implicit val decodeMarathonAppContainer: Decoder[MarathonAppContainer] = deriveFor[MarathonAppContainer].decoder
  implicit val decodeMarathonAppContainerDocker: Decoder[MarathonAppContainerDocker] = deriveFor[MarathonAppContainerDocker].decoder
  implicit val decodeMarathonApp: Decoder[MarathonApp] = deriveFor[MarathonApp].decoder
  implicit val decodeDescribeRequest: Decoder[DescribeRequest] = deriveFor[DescribeRequest].decoder
  implicit val decodePackageFiles: Decoder[PackageFiles] = deriveFor[PackageFiles].decoder
  implicit val decodeSearchRequest: Decoder[SearchRequest] = deriveFor[SearchRequest].decoder
  implicit val decodeSearchResponse: Decoder[SearchResponse] = deriveFor[SearchResponse].decoder
  implicit val decodeInstallRequest: Decoder[InstallRequest] = deriveFor[InstallRequest].decoder
  implicit val decodeInstallResponse: Decoder[InstallResponse] = deriveFor[InstallResponse].decoder
  implicit val decodeUninstallRequest: Decoder[UninstallRequest] = deriveFor[UninstallRequest].decoder
  implicit val decodeUninstallResponse: Decoder[UninstallResponse] = deriveFor[UninstallResponse].decoder
  implicit val decodeUninstallResult: Decoder[UninstallResult] = deriveFor[UninstallResult].decoder

  implicit val decodeCommandDefinition: Decoder[CommandDefinition] = deriveFor[CommandDefinition].decoder
  implicit val decodeDescribeResponse: Decoder[DescribeResponse] = deriveFor[DescribeResponse].decoder
  implicit val decodeListVersionsRequest: Decoder[ListVersionsRequest] = deriveFor[ListVersionsRequest].decoder
  implicit val decodeListVersionsResponse: Decoder[ListVersionsResponse] = deriveFor[ListVersionsResponse].decoder

  implicit val decodeErrorResponse: Decoder[ErrorResponse] = deriveFor[ErrorResponse].decoder

  implicit val decodeUri: Decoder[Uri] = Decoder.decodeString.map(Uri.parse)

  implicit val decodeListRequest: Decoder[ListRequest] = deriveFor[ListRequest].decoder
  implicit val decodeListResponse: Decoder[ListResponse] = deriveFor[ListResponse].decoder
  implicit val decodeInstallation: Decoder[Installation] = deriveFor[Installation].decoder
  implicit val decodePackageInformation: Decoder[PackageInformation] = deriveFor[PackageInformation].decoder

}
