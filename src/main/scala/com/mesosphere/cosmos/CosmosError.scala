package com.mesosphere.cosmos

import cats.data.NonEmptyList
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.model.AppId
import com.mesosphere.cosmos.model.thirdparty.marathon.MarathonError
import com.mesosphere.universe.PackageDetailsVersion
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import io.circe.{JsonObject, Json}
import io.circe.syntax._
import org.jboss.netty.handler.codec.http.HttpMethod

sealed abstract class CosmosError(causedBy: Throwable = null /*java compatibility*/) extends RuntimeException(causedBy) {

  def status: Status = Status.BadRequest

  def getData: Option[JsonObject] = None
}

case class PackageNotFound(packageName: String) extends CosmosError
case class VersionNotFound(packageName: String, packageVersion: PackageDetailsVersion) extends CosmosError
case class EmptyPackageImport() extends CosmosError
case class PackageFileMissing(packageName: String, cause: Throwable = null) extends CosmosError(cause)
case class PackageFileNotJson(fileName: String, parseError: String) extends CosmosError
case class PackageFileSchemaMismatch(fileName: String) extends CosmosError
case class PackageAlreadyInstalled() extends CosmosError {
  override val status = Status.Conflict
}
case class MarathonBadResponse(marathonError: MarathonError) extends CosmosError {
  val details = marathonError.details match {
    case Some(details) => Some(JsonObject.singleton("errors", details.asJson))
    case None => None
  }
  override def getData: Option[JsonObject] = details
}
case class MarathonGenericError(marathonStatus: Status) extends CosmosError {
  override val status = Status.InternalServerError
}
case class MarathonBadGateway(marathonStatus: Status) extends CosmosError {
  override val status = Status.BadGateway
}
case class IndexNotFound(repoUri: Uri) extends CosmosError
case class RepositoryNotFound(repoUri: Uri) extends CosmosError

case class MarathonAppMetadataError(note: String) extends CosmosError
case class MarathonAppDeleteError(appId: AppId) extends CosmosError
case class MarathonAppNotFound(appId: AppId) extends CosmosError
case class MesosRequestError(note: String) extends CosmosError
case class CirceError(cerr: io.circe.Error) extends CosmosError

case class UnsupportedContentType(supported: List[MediaType], actual: Option[MediaType] = None) extends CosmosError

case class GenericHttpError(method: HttpMethod, uri: Uri, override val status: Status) extends CosmosError

case class AmbiguousAppId(packageName: String, appIds: List[AppId]) extends CosmosError
case class MultipleFrameworkIds(
  packageName: String,
  packageVersion: Option[PackageDetailsVersion],
  frameworkName: String,
  ids: List[String]
) extends CosmosError
case class PackageNotInstalled(packageName: String) extends CosmosError

case class NelErrors(errs: NonEmptyList[CosmosError]) extends CosmosError //TODO: Cleanup

case class JsonSchemaMismatch(errors: Iterable[Json]) extends CosmosError {
  override def getData: Option[JsonObject] = Some(JsonObject.singleton("errors", errors.asJson))
}

case class FileUploadError(message: String) extends CosmosError { override val status = Status.NotImplemented }

case class UninstallNonExistentAppForPackage(packageName: String, appId: AppId) extends CosmosError

case class ServiceUnavailable(
  serviceName: String,
  causedBy: Throwable
) extends CosmosError(causedBy) {
  override val status = Status.ServiceUnavailable
  override val getData = Some(JsonObject.fromMap(Map(
    "serviceName" -> serviceName.asJson
  )))
}

case class IncompleteUninstall(packageName: String, causedBy: Throwable) extends CosmosError(causedBy)
