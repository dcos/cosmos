package com.mesosphere.cosmos

import cats.data.NonEmptyList
import com.mesosphere.cosmos.model.AppId
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import io.circe.Json
import io.circe.syntax._

sealed trait CosmosError extends RuntimeException {

  def status: Status = Status.BadRequest

  override def getMessage: String = {
    this match {
      case PackageNotFound(packageName) => s"Package [$packageName] not found"
      case VersionNotFound(packageName, packageVersion) =>
        s"Version [$packageVersion] of package [$packageName] not found"
      case EmptyPackageImport() => "Package is empty"
      case PackageFileMissing(fileName) => s"Package file [$fileName] not found"
      case PackageFileNotJson(fileName, parseError) =>
        s"Package file [$fileName] is not JSON: $parseError"
      case PackageFileSchemaMismatch(fileName) => s"Package file [$fileName] does not match schema"
      case PackageAlreadyInstalled() => "Package is already installed"
      case MarathonBadResponse(marathonStatus) =>
        s"Received response status code ${marathonStatus.code} from Marathon"
      case MarathonBadGateway(marathonStatus) =>
        s"Received response status code ${marathonStatus.code} from Marathon"
      case IndexNotFound(repoUri) => s"Index file missing for repo [$repoUri]"
      case RepositoryNotFound() => "No repository found"
      case MarathonAppMetadataError(note) => note
      case MarathonAppDeleteError(appId) => s"Error while deleting marathon app '$appId'"
      case MarathonAppNotFound(appId) => s"Unable to locate service with marathon appId: '$appId'"
      case CirceError(cerr) => cerr.getMessage
      case MesosRequestError(note) => note
      case JsonSchemaMismatch(_) => "Options JSON failed validation"

      // TODO(jose): Looking at the code these are all recursive!
      case uct @ UnsupportedContentType(_, _) => uct.toString
      case ghe @ GenericHttpError(uri, status) => ghe.toString
      case AmbiguousAppId(pkgName, appIds) => s"Multiple apps named [$pkgName] are installed: [${appIds.mkString(", ")}]"
      case mfi @ MultipleFrameworkIds(_, _) => mfi.toString
      case me @ MultipleError(_) => me.toString
      case NelErrors(nelE) => nelE.toString
      case FileUploadError(msg) => msg
      case PackageNotInstalled(pkgName) => s"Package [$pkgName] is not installed"
    }
  }

  def getData: Json = Json.obj()

}

case class PackageNotFound(packageName: String) extends CosmosError
case class VersionNotFound(packageName: String, packageVersion: String) extends CosmosError
case class EmptyPackageImport() extends CosmosError
case class PackageFileMissing(packageName: String) extends CosmosError
case class PackageFileNotJson(fileName: String, parseError: String) extends CosmosError
case class PackageFileSchemaMismatch(fileName: String) extends CosmosError
case class PackageAlreadyInstalled() extends CosmosError {
  override val status = Status.Conflict
}
case class MarathonBadResponse(marathonStatus: Status) extends CosmosError {
  override val status = Status.InternalServerError
}
case class MarathonBadGateway(marathonStatus: Status) extends CosmosError {
  override val status = Status.BadGateway
}
case class IndexNotFound(repoUri: Uri) extends CosmosError
case class RepositoryNotFound() extends CosmosError

case class MarathonAppMetadataError(note: String) extends CosmosError
case class MarathonAppDeleteError(appId: AppId) extends CosmosError
case class MarathonAppNotFound(appId: AppId) extends CosmosError
case class MesosRequestError(note: String) extends CosmosError
case class CirceError(cerr: io.circe.Error) extends CosmosError

case class UnsupportedContentType(contentType: Option[String], supported: String) extends CosmosError

case class GenericHttpError(uri: Uri, override val status: Status) extends CosmosError

case class AmbiguousAppId(packageName: String, appIds: List[AppId]) extends CosmosError
case class MultipleFrameworkIds(frameworkName: String, ids: List[String]) extends CosmosError
case class PackageNotInstalled(packageName: String) extends CosmosError

case class MultipleError(errs: List[CosmosError]) extends CosmosError
case class NelErrors(errs: NonEmptyList[CosmosError]) extends CosmosError //TODO: Cleanup

case class JsonSchemaMismatch(errors: Iterable[Json]) extends CosmosError {
  override def getData: Json = errors.asJson
}

case class FileUploadError(message: String) extends CosmosError { override val status = Status.NotImplemented }
