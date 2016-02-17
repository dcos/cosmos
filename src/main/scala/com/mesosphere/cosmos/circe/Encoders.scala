package com.mesosphere.cosmos.circe

import java.nio.ByteBuffer
import java.util.Base64

import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.model.thirdparty.marathon._
import com.mesosphere.cosmos.model.thirdparty.mesos.master._
import com.mesosphere.cosmos._
import com.mesosphere.universe._
import com.netaporter.uri.Uri
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.{ObjectEncoder, Encoder, Json, JsonObject}
import io.finch.Error

object Encoders {
  implicit val encodeLicense: Encoder[License] = deriveFor[License].encoder
  implicit val encodePackageDefinition: Encoder[PackageDetails] = deriveFor[PackageDetails].encoder
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
  implicit val encodePackageIndex: Encoder[UniverseIndexEntry] = ObjectEncoder.instance( entry => {
    JsonObject.fromIndexedSeq(
      Vector(
        "name" -> entry.name.asJson,
        "currentVersion" -> entry.currentVersion.asJson,
        "versions" -> encodeMap(entry.versions),
        "description" -> entry.description.asJson,
        "framework" -> entry.framework.asJson,
        "tags" -> entry.tags.asJson
      )
    )
  })
  implicit val encodeUniverseIndex: Encoder[UniverseIndex] = deriveFor[UniverseIndex].encoder
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

  implicit val encodeCommandDefinition: Encoder[Command] = deriveFor[Command].encoder
  implicit val encodeDescribeResponse: Encoder[DescribeResponse] = deriveFor[DescribeResponse].encoder
  implicit val encodeListVersionsRequest: Encoder[ListVersionsRequest] = deriveFor[ListVersionsRequest].encoder
  implicit val encodeListVersionsResponse: Encoder[ListVersionsResponse] = ObjectEncoder.instance( response => {
    JsonObject.singleton("results", encodeMap(response.results))
  })

  implicit val encodeErrorResponse: Encoder[ErrorResponse] = deriveFor[ErrorResponse].encoder
  implicit val encodeMarathonError: Encoder[MarathonError] = deriveFor[MarathonError].encoder

  implicit val encodeUri: Encoder[Uri] = Encoder.instance(_.toString.asJson)

  implicit val encodeListRequest: Encoder[ListRequest] = deriveFor[ListRequest].encoder
  implicit val encodeListResponse: Encoder[ListResponse] = deriveFor[ListResponse].encoder
  implicit val encodeInstallation: Encoder[Installation] = deriveFor[Installation].encoder
  implicit val encodePackageInformation: Encoder[InstalledPackageInformation] = deriveFor[InstalledPackageInformation].encoder

  implicit val encodeUniverseVersion: Encoder[UniverseVersion] = Encoder.instance(_.toString.asJson)
  implicit val encodePackagingVersion: Encoder[PackagingVersion] = Encoder.instance(_.toString.asJson)
  implicit val encodePackageRevision: Encoder[ReleaseVersion] = Encoder.instance(_.toString.asJson)
  implicit val encodePackageDetailsVersion: Encoder[PackageDetailsVersion] = Encoder.instance(_.toString.asJson)

  implicit val encodeCapabilitiesResponse: Encoder[CapabilitiesResponse] = deriveFor[CapabilitiesResponse].encoder
  implicit val encodeCapability: Encoder[Capability] = deriveFor[Capability].encoder

  implicit val encodePackageRepositoryListRequest: Encoder[PackageRepositoryListRequest] = {
    deriveFor[PackageRepositoryListRequest].encoder
  }
  implicit val encodePackageRepositoryListResponse: Encoder[PackageRepositoryListResponse] = {
    deriveFor[PackageRepositoryListResponse].encoder
  }
  implicit val encodePackageRepository: Encoder[PackageRepository] = {
    deriveFor[PackageRepository].encoder
  }
  implicit val encodePackageRepositoryAddRequest: Encoder[PackageRepositoryAddRequest] = {
    deriveFor[PackageRepositoryAddRequest].encoder
  }
  implicit val encodePackageRepositoryAddResponse: Encoder[PackageRepositoryAddResponse] = {
    deriveFor[PackageRepositoryAddResponse].encoder
  }
  implicit val encodePackageRepositoryDeleteRequest: Encoder[PackageRepositoryDeleteRequest] = {
    deriveFor[PackageRepositoryDeleteRequest].encoder
  }
  implicit val encodePackageRepositoryDeleteResponse: Encoder[PackageRepositoryDeleteResponse] = {
    deriveFor[PackageRepositoryDeleteResponse].encoder
  }

  implicit val encodeZooKeeperStorageEnvelope: Encoder[ZooKeeperStorageEnvelope] =
    deriveFor[ZooKeeperStorageEnvelope].encoder

  implicit val exceptionEncoder: Encoder[Exception] = {
    Encoder.instance { e => exceptionErrorResponse(e).asJson }
  }

  implicit val encodeByteBuffer: Encoder[ByteBuffer] = Encoder.instance { bb =>
    Base64.getEncoder.encodeToString(ByteBuffers.getBytes(bb)).asJson
  }

  private[this] def exceptionErrorResponse(t: Throwable): ErrorResponse = t match {
    case Error.NotPresent(item) =>
      ErrorResponse("not_present", s"Item '${item.description}' not present but required")
    case Error.NotParsed(item, typ, cause) =>
      ErrorResponse("not_parsed", s"Item '${item.description}' unable to be parsed : '${cause.getMessage}'")
    case Error.NotValid(item, rule) =>
      ErrorResponse("not_valid", s"Item '${item.description}' deemed invalid by rule: '$rule'")
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
    case VersionNotFound(packageName, PackageDetailsVersion(packageVersion)) =>
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
    case MarathonBadResponse(marathonErr) => marathonErr.message
    case MarathonGenericError(marathonStatus) =>
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

    case ServiceUnavailable(serviceName, _) =>
      s"Unable to complete request due to downstream service [$serviceName] unavailability"
    case IncompleteUninstall(packageName, _) =>
      s"Incomplete uninstall of package [$packageName] due to Mesos unavailability"

    case RepoNameOrUriMissing() =>
      s"Must specify either the name or URI of the repository"
    case ZooKeeperStorageError(msg) => msg
    case ConcurrentAccess(_) =>
      s"Retry operation. Operation didn't complete due to concurrent access."
  }

  private[this] def encodeMap(versions: Map[PackageDetailsVersion, ReleaseVersion]): Json = {
    versions
      .map {
        case (PackageDetailsVersion(pdv), ReleaseVersion(rv)) => pdv -> rv
      }.asJson
  }
}
