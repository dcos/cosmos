package com.mesosphere.cosmos.circe

import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.model.mesos.master._
import com.mesosphere.cosmos.{CosmosError, ErrorResponse, ErrorResponseEntry}
import com.netaporter.uri.Uri
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.finch.Error

object Encoders {
  implicit val encodeLicense: Encoder[License] = deriveFor[License].encoder
  implicit val encodePackageDefinition: Encoder[PackageDefinition] = deriveFor[PackageDefinition].encoder
  implicit val encodeContainer: Encoder[Container] = deriveFor[Container].encoder
  implicit val encodeAssets: Encoder[Assets] = deriveFor[Assets].encoder
  implicit val encodeImages: Encoder[Images] = deriveFor[Images].encoder
  implicit val encodeResource: Encoder[Resource] = deriveFor[Resource].encoder
  implicit val encodePackageIndex: Encoder[UniverseIndexEntry] = deriveFor[UniverseIndexEntry].encoder
  implicit val encodeUniverseIndex: Encoder[UniverseIndex] = deriveFor[UniverseIndex].encoder
  implicit val encodePackageInfo: Encoder[PackageInfo] = deriveFor[PackageInfo].encoder
  implicit val encodeMasterState: Encoder[MasterState] = deriveFor[MasterState].encoder
  implicit val encodeFramework: Encoder[Framework] = deriveFor[Framework].encoder
  implicit val encodeMesosFrameworkTearDownResponse: Encoder[MesosFrameworkTearDownResponse] = deriveFor[MesosFrameworkTearDownResponse].encoder
  implicit val encodeMarathonAppResponse: Encoder[MarathonAppResponse] = deriveFor[MarathonAppResponse].encoder
  implicit val encodeMarathonAppsResponse: Encoder[MarathonAppsResponse] = deriveFor[MarathonAppsResponse].encoder
  implicit val encoder: Encoder[AppId] = Encoder.instance(_.toString.asJson)
  implicit val encodeMarathonAppContainer: Encoder[MarathonAppContainer] = deriveFor[MarathonAppContainer].encoder
  implicit val encodeMarathonAppContainerDocker: Encoder[MarathonAppContainerDocker] = deriveFor[MarathonAppContainerDocker].encoder
  implicit val encodeMarathonApp: Encoder[MarathonApp] = deriveFor[MarathonApp].encoder
  implicit val encodeDescribeRequest: Encoder[DescribeRequest] = deriveFor[DescribeRequest].encoder
  implicit val encodePackageFiles: Encoder[PackageFiles] = deriveFor[PackageFiles].encoder
  implicit val encodeSearchRequest: Encoder[SearchRequest] = deriveFor[SearchRequest].encoder
  implicit val encodeSearchResponse: Encoder[SearchResponse] = deriveFor[SearchResponse].encoder
  implicit val encodeInstallRequest: Encoder[InstallRequest] = deriveFor[InstallRequest].encoder
  implicit val encodeInstallResponse: Encoder[InstallResponse] = deriveFor[InstallResponse].encoder
  implicit val encodeUninstallRequest: Encoder[UninstallRequest] = deriveFor[UninstallRequest].encoder
  implicit val encodeUninstallResponse: Encoder[UninstallResponse] = deriveFor[UninstallResponse].encoder
  implicit val encodeUninstallResult: Encoder[UninstallResult] = deriveFor[UninstallResult].encoder

  implicit val encodeCommandDefinition: Encoder[CommandDefinition] = deriveFor[CommandDefinition].encoder
  implicit val encodeDescribeResponse: Encoder[DescribeResponse] = deriveFor[DescribeResponse].encoder
  implicit val encodeListVersionsRequest: Encoder[ListVersionsRequest] = deriveFor[ListVersionsRequest].encoder
  implicit val encodeListVersionsResponse: Encoder[ListVersionsResponse] = deriveFor[ListVersionsResponse].encoder

  implicit val encodeErrorResponseEntry: Encoder[ErrorResponseEntry] = deriveFor[ErrorResponseEntry].encoder
  implicit val encodeErrorResponse: Encoder[ErrorResponse] = deriveFor[ErrorResponse].encoder

  implicit val encodeUri: Encoder[Uri] = Encoder.instance(_.toString.asJson)

  implicit val encodeListRequest: Encoder[ListRequest] = deriveFor[ListRequest].encoder
  implicit val encodeListResponse: Encoder[ListResponse] = deriveFor[ListResponse].encoder
  implicit val encodeInstallation: Encoder[Installation] = deriveFor[Installation].encoder
  implicit val encodePackageInformation: Encoder[PackageInformation] = deriveFor[PackageInformation].encoder

  implicit val exceptionEncoder: Encoder[Exception] =
    Encoder.instance { e => ErrorResponse(exceptionErrorResponse(e)).asJson }

  private[this] def exceptionErrorResponse(t: Throwable): List[ErrorResponseEntry] = t match {
    case Error.NotPresent(item) =>
      List(ErrorResponseEntry("not_present", s"Item '${item.kind}' not present but required"))
    case Error.NotParsed(item, typ, cause) =>
      List(ErrorResponseEntry("not_parsed", s"Item '${item.kind}' unable to be parsed : '${cause.getMessage}'"))
    case Error.NotValid(item, rule) =>
      List(ErrorResponseEntry("not_valid", s"Item '${item.kind}' deemed invalid by rule: '$rule'"))
    case Error.RequestErrors(ts) =>
      ts.flatMap(exceptionErrorResponse).toList
    case ce: CosmosError =>
      List(ErrorResponseEntry(ce.getClass.getSimpleName, ce.getMessage))
    case t: Throwable =>
      List(ErrorResponseEntry("unhandled_exception", t.getMessage))
  }

}
