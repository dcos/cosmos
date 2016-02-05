package com.mesosphere.cosmos.circe

import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.model.mesos.master._
import com.mesosphere.cosmos._
import com.netaporter.uri.Uri
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.{Encoder, Json, JsonObject}
import io.finch.Error

object Encoders {
  implicit val encodeLicense: Encoder[License] = deriveFor[License].encoder
  implicit val encodePackageDefinition: Encoder[PackageDefinition] = deriveFor[PackageDefinition].encoder
  implicit val encodeContainer: Encoder[Container] = deriveFor[Container].encoder
  implicit val encodeAssets: Encoder[Assets] = deriveFor[Assets].encoder
  implicit val encodeImages: Encoder[Images] = Encoder.instance { (images: Images) =>
    Json.obj(
      "icon-small" -> images.iconSmall.asJson,
      "icon-medium" -> images.iconMedium.asJson,
      "icon-large" -> images.iconLarge.asJson,
      "screenshots" -> images.screenshots.asJson
    )
  }
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

  implicit val encodeRenderRequest: Encoder[RenderRequest] = deriveFor[RenderRequest].encoder
  implicit val encodeRenderResponse: Encoder[RenderResponse] = deriveFor[RenderResponse].encoder

  implicit val encodeCommandDefinition: Encoder[CommandDefinition] = deriveFor[CommandDefinition].encoder
  implicit val encodeDescribeResponse: Encoder[DescribeResponse] = deriveFor[DescribeResponse].encoder
  implicit val encodeListVersionsRequest: Encoder[ListVersionsRequest] = deriveFor[ListVersionsRequest].encoder
  implicit val encodeListVersionsResponse: Encoder[ListVersionsResponse] = deriveFor[ListVersionsResponse].encoder

  implicit val encodeErrorResponse: Encoder[ErrorResponse] = deriveFor[ErrorResponse].encoder

  implicit val encodeUri: Encoder[Uri] = Encoder.instance(_.toString.asJson)

  implicit val encodeListRequest: Encoder[ListRequest] = deriveFor[ListRequest].encoder
  implicit val encodeListResponse: Encoder[ListResponse] = deriveFor[ListResponse].encoder
  implicit val encodeInstallation: Encoder[Installation] = deriveFor[Installation].encoder
  implicit val encodePackageInformation: Encoder[PackageInformation] = deriveFor[PackageInformation].encoder

  implicit val exceptionEncoder: Encoder[Exception] =
    Encoder.instance { e => exceptionErrorResponse(e).asJson }

  private[this] def exceptionErrorResponse(t: Throwable): ErrorResponse = t match {
    case Error.NotPresent(item) =>
      ErrorResponse("not_present", s"Item '${item.kind}' not present but required")
    case Error.NotParsed(item, typ, cause) =>
      ErrorResponse("not_parsed", s"Item '${item.kind}' unable to be parsed : '${cause.getMessage}'")
    case Error.NotValid(item, rule) =>
      ErrorResponse("not_valid", s"Item '${item.kind}' deemed invalid by rule: '$rule'")
    case Error.RequestErrors(ts) =>
      val details = ts.map(exceptionErrorResponse).toList.asJson
      ErrorResponse(
        "multiple_errors",
        "Multiple errors while processing request",
        Some(JsonObject.singleton("errors", details))
      )
    case ce: CosmosError =>
      ErrorResponse(ce.getClass.getSimpleName, msgForCosmosError(ce), ce.getData)
    case t: Throwable =>
      ErrorResponse("unhandled_exception", t.getMessage)
  }

  private[this] def msgForCosmosError(err: CosmosError): String = err match {
    case PackageNotFound(packageName) =>
      s"Package [$packageName] not found"
    case VersionNotFound(packageName, packageVersion) =>
      s"Version [$packageVersion] of package [$packageName] not found"
    case EmptyPackageImport() =>
      "Package is empty"
    case PackageFileMissing(fileName, _) =>
      s"Package file [$fileName] not found"
    case PackageFileNotJson(fileName, parseError) =>
      s"Package file [$fileName] is not JSON: $parseError"
    case PackageFileSchemaMismatch(fileName) =>
      s"Package file [$fileName] does not match schema"
    case PackageAlreadyInstalled() =>
      "Package is already installed"
    case MarathonBadResponse(marathonStatus) =>
      s"Received response status code ${marathonStatus.code} from Marathon"
    case MarathonBadGateway(marathonStatus) =>
      s"Received response status code ${marathonStatus.code} from Marathon"
    case IndexNotFound(repoUri) =>
      s"Index file missing for repo [$repoUri]"
    case RepositoryNotFound(repoUri) =>
      s"No repository found [$repoUri]"
    case MarathonAppMetadataError(note) => note
    case MarathonAppDeleteError(appId) =>
      s"Error while deleting marathon app '$appId'"
    case MarathonAppNotFound(appId) =>
      s"Unable to locate service with marathon appId: '$appId'"
    case CirceError(cerr) => cerr.getMessage
    case MesosRequestError(note) => note
    case JsonSchemaMismatch(_) =>
      "Options JSON failed validation"
    case UnsupportedContentType(supported, actual) =>
      val acceptMsg = supported.map(_.show).mkString("[", ", ", "]")
      actual match {
        case Some(mt) =>
          s"Unsupported Content-Type: ${mt.show} Accept: $acceptMsg"
        case None =>
          s"Unspecified Content-Type Accept: $acceptMsg"
      }
    case GenericHttpError(method, uri, status) =>
      s"Unexpected down stream http error: ${method.getName} ${uri.toString} ${status.code}"
    case AmbiguousAppId(pkgName, appIds) =>
      s"Multiple apps named [$pkgName] are installed: [${appIds.mkString(", ")}]"
    case MultipleFrameworkIds(pkgName, pkgVersion, fwName, ids) =>
      pkgVersion match {
        case Some(ver) =>
          s"Uninstalled package [$pkgName] version [$ver]\n" +
            s"Unable to shutdown [$pkgName] service framework with name [$fwName] because there are multiple framework " +
            s"ids matching this name: [${ids.mkString(", ")}]"
        case None =>
          s"Uninstalled package [$pkgName]\n" +
            s"Unable to shutdown [$pkgName] service framework with name [$fwName] because there are multiple framework " +
            s"ids matching this name: [${ids.mkString(", ")}]"
      }
    case NelErrors(nelE) => nelE.toString
    case FileUploadError(msg) => msg
    case PackageNotInstalled(pkgName) =>
      s"Package [$pkgName] is not installed"
    case UninstallNonExistentAppForPackage(pkgName, appId) =>
      s"Package [$pkgName] with id [$appId] is not installed"
  }
}
