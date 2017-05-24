package com.mesosphere.cosmos

import _root_.io.circe.Decoder
import _root_.io.circe.DecodingFailure
import _root_.io.circe.Encoder
import _root_.io.circe.Json
import _root_.io.circe.JsonObject
import _root_.io.circe.generic.semiauto.deriveDecoder
import _root_.io.circe.generic.semiauto.deriveEncoder
import _root_.io.circe.syntax._
import cats.data.Ior
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonError
import com.mesosphere.universe
import com.mesosphere.universe.common.circe.Encoders._
import com.mesosphere.universe.v2.circe.Encoders._
import com.mesosphere.util.PackageUtil
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import org.jboss.netty.handler.codec.http.HttpMethod
import scala.util.control.NoStackTrace

// scalastyle:off number.of.types
// TODO: set the message string for RuntimeException
final class CosmosException(
  error: CosmosError,
  status: Status = Status.BadRequest,
  headers: Map[String, String] = Map.empty,
  causedBy: Option[Throwable] = None
) extends RuntimeException(causedBy.orNull) {
}

trait CosmosError {
  def message: String
  def data: Option[JsonObject]
}

object CosmosError {
  def deriveData[T <: CosmosError](error: T)(implicit encoder: Encoder[T]): Option[JsonObject] = {
    encoder(error).asObject
  }
}


final case class InvalidPackageVersionForAdd(
  packageName: String,
  packageVersion: universe.v3.model.Version
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object InvalidPackageVersionForAdd {
  def apply(packageCoordinate: rpc.v1.model.PackageCoordinate): InvalidPackageVersionForAdd = {
    InvalidPackageVersionForAdd(packageCoordinate.name, packageCoordinate.version)
  }

  implicit val encoder: Encoder[InvalidPackageVersionForAdd] = deriveEncoder
  implicit val decoder: Decoder[InvalidPackageVersionForAdd] = deriveDecoder
}


final case class PackageNotFound(packageName: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object PackageNotFound {
  implicit val encoder: Encoder[PackageNotFound] = deriveEncoder
  implicit val decoder: Decoder[PackageNotFound] = deriveDecoder
}


final case class VersionNotFound(
  packageName: String,
  packageVersion: universe.v3.model.Version
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override val message: String = ???
}

object VersionNotFound {
  implicit val encoder: Encoder[VersionNotFound] = deriveEncoder
  implicit val decoder: Decoder[VersionNotFound] = deriveDecoder
}


final case class PackageFileMissing(
  packageName: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override val message: String = ???
}

object PackageFileMissing {
  implicit val encoder: Encoder[PackageFileMissing] = deriveEncoder
  implicit val decoder: Decoder[PackageFileMissing] = deriveDecoder
}


final case class PackageFileNotJson(fileName: String, parseError: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override val message: String = ???
}

object PackageFileNotJson {
  implicit val encoder: Encoder[PackageFileNotJson] = deriveEncoder
  implicit val decoder: Decoder[PackageFileNotJson] = deriveDecoder
}


final case class UnableToParseMarathonAsJson(parseError: String) extends  CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override val message: String = ???
}

object UnableToParseMarathonAsJson {
  implicit val encoder: Encoder[UnableToParseMarathonAsJson] = deriveEncoder
  implicit val decoder: Decoder[UnableToParseMarathonAsJson] = deriveDecoder
}

final case class PackageFileSchemaMismatch(fileName: String, decodingFailure: DecodingFailure) extends CosmosError {
  // TODO: See we can fix this
  override def data: Option[JsonObject] = {
    Some(JsonObject.singleton("errorMessage", decodingFailure.getMessage().asJson))
  }
  override val message: String = ???
}

object PackageFileSchemaMismatch {
  // TODO: need to investigate DecodingFailure
}

// TODO: Why aren't we return data? Make this a CosmosError again
final case class PackageAlreadyInstalled() extends RuntimeException {
  override val status = Status.Conflict
  override val getData = None
}

final case class ServiceAlreadyStarted() extends RuntimeException {
  override val status = Status.Conflict
  override val getData = None
}

final case class MarathonBadResponse(marathonError: MarathonError) extends CosmosError {
  override def data: Option[JsonObject] = {
    marathonError.details.map(details => JsonObject.singleton("errors", details.asJson))
  }
  override val message: String = ???
}

final case class MarathonGenericError(marathonStatus: Status) extends CosmosError {
  // TODO: This is part of the CosmosException override val status = Status.InternalServerError
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override val message: String = ???
}

object MarathonGenericError {
  implicit val encoder: Encoder[MarathonGenericError] = deriveEncoder
  implicit val decoder: Decoder[MarathonGenericError] = deriveDecoder
}

final case class MarathonBadGateway(marathonStatus: Status) extends CosmosError {
  // TODO: This is part of the CosmosException override val status = Status.BadGateway
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override val message: String = ???
}

object MarathonBadGateway {
  implicit val encoder: Encoder[MarathonBadGateway] = deriveEncoder
  implicit val decoder: Decoder[MarathonBadGateway] = deriveDecoder
}


final case class IndexNotFound(repoUri: Uri) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override val message: String = ???
}

object IndexNotFound {
  implicit val encoder: Encoder[IndexNotFound] = deriveEncoder
  implicit val decoder: Decoder[IndexNotFound] = deriveDecoder
}


final case class MarathonAppDeleteError(appId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override val message: String = ???
}

object MarathonAppDeleteError {
  implicit val encoder: Encoder[MarathonAppDeleteError] = deriveEncoder
  implicit val decoder: Decoder[MarathonAppDeleteError] = deriveDecoder
}

final case class MarathonAppNotFound(appId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override val message: String = ???
}

object MarathonAppNotFound {
  implicit val encoder: Encoder[MarathonAppNotFound] = deriveEncoder
  implicit val decoder: Decoder[MarathonAppNotFound] = deriveDecoder
}

final case class CirceError(circeError: _root_.io.circe.Error) extends CosmosError {
  override val data = None
  override val message: String = ???
}

case object MarathonTemplateMustBeJsonObject extends CosmosError {
  override val data = None
  override val message: String = ???
}

final case class UnsupportedContentType(supported: List[MediaType], actual: Option[String] = None) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override val message: String = ???
}

object UnsupportedContentType {
  def forMediaType(
    supported: List[MediaType],
    actual: Option[MediaType]
  ): UnsupportedContentType = {
    new UnsupportedContentType(supported, actual.map(_.show))
  }

  implicit val encoder: Encoder[UnsupportedContentType] = deriveEncoder
  implicit val decoder: Decoder[UnsupportedContentType] = deriveDecoder
}


final case class UnsupportedContentEncoding(supported: List[String], actual: Option[String] = None) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object UnsupportedContentEncoding {
  implicit val encoder: Encoder[UnsupportedContentEncoding] = deriveEncoder
  implicit val decoder: Decoder[UnsupportedContentEncoding] = deriveDecoder
}

final case class UnsupportedRedirect(supported: List[String], actual: Option[String] = None) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object UnsupportedRedirect {
  implicit val encoder: Encoder[UnsupportedRedirect] = deriveEncoder
  implicit val decoder: Decoder[UnsupportedRedirect] = deriveDecoder
}

// TODO: Move status to CosmosException
final case class GenericHttpError(
  method: HttpMethod,
  uri: Uri,
  clientStatus: Status
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object GenericHttpError {
  implicit val encoder: Encoder[GenericHttpError] = deriveEncoder
  implicit val decoder: Decoder[GenericHttpError] = deriveDecoder
}

final case class AmbiguousAppId(packageName: String, appIds: List[AppId]) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object AmbiguousAppId {
  implicit val encoder: Encoder[AmbiguousAppId] = deriveEncoder
  implicit val decoder: Decoder[AmbiguousAppId] = deriveDecoder
}

final case class MultipleFrameworkIds(
  packageName: String,
  packageVersion: Option[universe.v2.model.PackageDetailsVersion],
  frameworkName: String,
  ids: List[String]
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object MultipleFrameworkIds {
  implicit val encoder: Encoder[MultipleFrameworkIds] = deriveEncoder
  implicit val decoder: Decoder[MultipleFrameworkIds] = deriveDecoder
}

final case class PackageNotInstalled(packageName: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object PackageNotInstalled {
  implicit val encoder: Encoder[PackageNotInstalled] = deriveEncoder
  implicit val decoder: Decoder[PackageNotInstalled] = deriveDecoder
}

final case class JsonSchemaMismatch(errors: Iterable[Json]) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object JsonSchemaMismatch {
  implicit val encoder: Encoder[JsonSchemaMismatch] = deriveEncoder
  implicit val decoder: Decoder[JsonSchemaMismatch] = deriveDecoder
}

final case class UninstallNonExistentAppForPackage(
  packageName: String,
  appId: AppId
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object UninstallNonExistentAppForPackage {
  implicit val encoder: Encoder[UninstallNonExistentAppForPackage] = deriveEncoder
  implicit val decoder: Decoder[UninstallNonExistentAppForPackage] = deriveDecoder
}


// TODO: move Throwable to CosmosException: causedBy: Throwable
final case class ServiceUnavailable(
  serviceName: String
) extends CosmosError {
  // TODO: move this to CosmosException: override val status = Status.ServiceUnavailable
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object ServiceUnavailable {
  implicit val encoder: Encoder[ServiceUnavailable] = deriveEncoder
  implicit val decoder: Decoder[ServiceUnavailable] = deriveDecoder
}


// TODO: Fix this cosmos error
final case class Unauthorized(
  serviceName: String,
  realm: Option[String]
) extends CosmosError with NoStackTrace {
  override val getMessage: String = {
    s"Unable to complete request due to Unauthorized response from service [$serviceName]"
  }

  override val status: Status = Status.Unauthorized

  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("serviceName", serviceName.asJson))
  }

  override val getHeaders: Map[String, String] = realm match {
    case Some(r) =>
      Map("WWW-Authenticate" -> r)
    case _ =>
      Map.empty
  }
}


// TODO: Fix this cosmos error
final case class Forbidden(serviceName: String) extends CosmosError with NoStackTrace {
  override val getMessage: String = {
    s"Unable to complete request due to Forbidden response from service [$serviceName]"
  }

  override val status: Status = Status.Forbidden

  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("serviceName", serviceName.asJson))
  }
}


// TODO: Move this to CosmosException: causedBy: Throwable
final case class IncompleteUninstall(
  packageName: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object IncompleteUninstall {
  implicit val encoder: Encoder[IncompleteUninstall] = deriveEncoder
  implicit val decoder: Decoder[IncompleteUninstall] = deriveDecoder
}


final case class ZooKeeperStorageError(msg: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object ZooKeeperStorageError {
  implicit val encoder: Encoder[ZooKeeperStorageError] = deriveEncoder
  implicit val decoder: Decoder[ZooKeeperStorageError] = deriveDecoder
}


// TODO: Move this to CosmosException: causedBy: Throwable
final case class ConcurrentAccess() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = ???
}


final case class RepoNameOrUriMissing() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = ???
}


final case class RepositoryAlreadyPresent(nameOrUri: Ior[String, Uri]) extends CosmosError {
  override def data: Option[JsonObject] = {
    val jsonMap = nameOrUri match {
      case Ior.Both(n, u) => Map("name" -> n.asJson, "uri" -> u.asJson)
      case Ior.Left(n) => Map("name" -> n.asJson)
      case Ior.Right(u) => Map("uri" -> u.asJson)
    }
    Some(JsonObject.fromMap(jsonMap))
  }
  override def message: String = ???
}


final case class RepositoryAddIndexOutOfBounds(attempted: Int, max: Int) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object RepositoryAddIndexOutOfBounds {
  implicit val encoder: Encoder[RepositoryAddIndexOutOfBounds] = deriveEncoder
  implicit val decoder: Decoder[RepositoryAddIndexOutOfBounds] = deriveDecoder
}

final case class UnsupportedRepositoryVersion(
  version: universe.v2.model.UniverseVersion
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object UnsupportedRepositoryVersion {
  implicit val encoder: Encoder[UnsupportedRepositoryVersion] = deriveEncoder
  implicit val decoder: Decoder[UnsupportedRepositoryVersion] = deriveDecoder
}

final case class UnsupportedRepositoryUri(uri: Uri) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object UnsupportedRepositoryUri {
  implicit val encoder: Encoder[UnsupportedRepositoryUri] = deriveEncoder
  implicit val decoder: Decoder[UnsupportedRepositoryUri] = deriveDecoder
}

// TODO: fix this cosmos error
final case class RepositoryUriSyntax(
  repository: rpc.v1.model.PackageRepository,
  causedBy: Throwable
) extends CosmosError(Some(causedBy)) {
  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("cause", causedBy.getMessage.asJson))
  }
}

// TODO: fix this cosmos error
final case class RepositoryUriConnection(
  repository: rpc.v1.model.PackageRepository,
  causedBy: Throwable
) extends CosmosError(Some(causedBy)) {
  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("cause", causedBy.getMessage.asJson))
  }
}


final case class RepositoryNotPresent(nameOrUri: Ior[String, Uri]) extends CosmosError {
  override def data: Option[JsonObject] = {
    val jsonMap = nameOrUri match {
      case Ior.Both(n, u) => Map("name" -> n.asJson, "uri" -> u.asJson)
      case Ior.Left(n) => Map("name" -> n.asJson)
      case Ior.Right(u) => Map("uri" -> u.asJson)
    }
    Some(JsonObject.fromMap(jsonMap))
  }
  override def message: String = ???
}


final case class ConversionError(failure: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object ConversionError {
  implicit val encoder: Encoder[ConversionError] = deriveEncoder
  implicit val decoder: Decoder[ConversionError] = deriveDecoder
}


final case class ServiceMarathonTemplateNotFound(
  packageName: String,
  packageVersion: universe.v3.model.Version
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object ServiceMarathonTemplateNotFound {
  implicit val encoder: Encoder[ServiceMarathonTemplateNotFound] = deriveEncoder
  implicit val decoder: Decoder[ServiceMarathonTemplateNotFound] = deriveDecoder
}


final case class InstallQueueError(msg: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object InstallQueueError {
  implicit val encoder: Encoder[InstallQueueError] = deriveEncoder
  implicit val decoder: Decoder[InstallQueueError] = deriveDecoder
}


// TODO: Move this to CosmosException: override val status = Status.NotImplemented
final case class NotImplemented(msg: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object NotImplemented {
  implicit val encoder: Encoder[NotImplemented] = deriveEncoder
  implicit val decoder: Decoder[NotImplemented] = deriveDecoder
}


// TODO: Move this to CosmosException: override val status = Status.Conflict
final case class OperationInProgress(coordinate: rpc.v1.model.PackageCoordinate) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object OperationInProgress {
  implicit val encoder: Encoder[OperationInProgress] = deriveEncoder
  implicit val decoder: Decoder[OperationInProgress] = deriveDecoder
}


// TODO: Fix this cosmos error
final case class InvalidPackage(
  reason: PackageUtil.PackageError
) extends CosmosError(Some(reason)) {
  override val getData: Option[JsonObject] = Some(reason.getData)
}


final case class ConversionFromPackageToV1AddResponse() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def msg: String = "A v4 package cannot be converted into a v1 AddResponse"
}


final case class ConversionFromPackageToV2DescribeResponse() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def msg: String = "A v4 package cannot be converted into a v2 DescribeResponse"
}


final case class OptionsNotStored(
  override val msg: String
) extends CosmosError {
  override def data: Option[JsonObject] = None
}


final case class AppIdChanged(
  override val msg: String
) extends CosmosError {
  override def data: Option[JsonObject] = None
}


final case class OptionsConflict(
  override val msg: String
) extends CosmosError {
  override def data: Option[JsonObject] = None
}


final case class BadVersionUpdate(
  currentVersion: universe.v3.model.Version,
  updateVersion: universe.v3.model.Version,
  validVersions: List[universe.v3.model.Version]
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = ???
}

object BadVersionUpdate {
  implicit val encoder: Encoder[BadVersionUpdate] = deriveEncoder
  implicit val decoder: Decoder[BadVersionUpdate] = deriveDecoder
}


final case class ServiceUpdateError(
  override val msg: String
) extends CosmosError {
  override def data: Option[JsonObject] = None
}

// scalastyle:on number.of.types
