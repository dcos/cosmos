package com.mesosphere.cosmos

import _root_.io.circe.syntax._
import _root_.io.circe.{DecodingFailure, Json, JsonObject}
import cats.data.Ior
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.finch.RequestError
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.thirdparty.marathon.model.{AppId, MarathonError}
import com.mesosphere.universe
import com.mesosphere.universe.common.circe.Encoders._
import com.mesosphere.universe.v3.model.PackageDefinition
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import org.jboss.netty.handler.codec.http.HttpMethod

import scala.util.control.NoStackTrace

// scalastyle:off number.of.types
sealed abstract class CosmosError(causedBy: Option[Throwable] = None) extends RequestError(causedBy) {

  def errType: String = this.getClass.getSimpleName

  def status: Status = Status.BadRequest

  def getData: Option[JsonObject] = {
    /* Circe encodes sum types like CosmosError into JSON as follows:
     * {
     *   "PackageNotFound": {
     *     "packageName": "cassandra"
     *   }
     * }
     *
     * For this method, we just want the field values, so the code below extracts the nested object
     * from the JSON that Circe generates.
     */
    this.asJson
      .asObject
      .flatMap(_.values.headOption)
      .flatMap(_.asObject)
  }

  def getHeaders: Map[String, String] = Map.empty
}

case class ConcurrentPackageUpdateDuringPublish() extends CosmosError {
  override val status = Status.Conflict
}

case class PackageNotFound(packageName: String) extends CosmosError

case class VersionNotFound(
  packageName: String,
  packageVersion: universe.v3.model.PackageDefinition.Version
) extends CosmosError

case class PackageFileMissing(packageName: String, cause: Option[Throwable] = None) extends CosmosError(cause)
case class PackageFileNotJson(fileName: String, parseError: String) extends CosmosError
case class UnableToParseMarathonAsJson(parseError: String) extends  CosmosError
case class PackageFileSchemaMismatch(fileName: String, decodingFailure: DecodingFailure) extends CosmosError {
  override def getData: Option[JsonObject] = {
    Some(JsonObject.singleton("errorMessage", decodingFailure.getMessage().asJson))
  }
}
case class PackageAlreadyInstalled() extends CosmosError {
  override val status = Status.Conflict
}

case class MarathonBadResponse(marathonError: MarathonError) extends CosmosError {
  override def getData: Option[JsonObject] = {
    marathonError.details.map(details => JsonObject.singleton("errors", details.asJson))
  }
}

case class MarathonGenericError(marathonStatus: Status) extends CosmosError {
  override val status = Status.InternalServerError
}
case class MarathonBadGateway(marathonStatus: Status) extends CosmosError {
  override val status = Status.BadGateway
}
case class IndexNotFound(repoUri: Uri) extends CosmosError

case class MarathonAppDeleteError(appId: AppId) extends CosmosError
case class MarathonAppNotFound(appId: AppId) extends CosmosError
case class CirceError(circeError: _root_.io.circe.Error) extends CosmosError
case object MarathonTemplateMustBeJsonObject extends CosmosError

case class UnsupportedContentType(supported: List[MediaType], actual: Option[String] = None) extends CosmosError {
  override def getData: Option[JsonObject] = {
    Some(JsonObject.fromMap(Map(
      "supported" -> supported.asJson,
      "actual" -> actual.asJson
    )))
  }
}
object UnsupportedContentType {
  def forMediaType(supported: List[MediaType], actual: Option[MediaType]): UnsupportedContentType = {
    new UnsupportedContentType(supported, actual.map(_.show))
  }
}

case class UnsupportedContentEncoding(supported: List[String], actual: Option[String] = None) extends CosmosError {
  override def getData: Option[JsonObject] = {
    Some(JsonObject.fromMap(Map(
      "supported" -> supported.asJson,
      "actual" -> actual.asJson
    )))
  }
}

case class UnsupportedRedirect(supported: List[String], actual: Option[String] = None) extends CosmosError {
  override def getData: Option[JsonObject] = {
    Some(JsonObject.fromMap(Map(
      "supported" -> supported.asJson,
      "actual" -> actual.asJson
    )))
  }
}

case class GenericHttpError(method: HttpMethod, uri: Uri, override val status: Status) extends CosmosError
object GenericHttpError {
  def apply(method: String, uri: Uri, status: Int): GenericHttpError = {
    new GenericHttpError(HttpMethod.valueOf(method.toUpperCase), uri, Status.fromCode(status))
  }
}

case class AmbiguousAppId(packageName: String, appIds: List[AppId]) extends CosmosError
case class MultipleFrameworkIds(
  packageName: String,
  packageVersion: Option[universe.v2.model.PackageDetailsVersion],
  frameworkName: String,
  ids: List[String]
) extends CosmosError
case class PackageNotInstalled(packageName: String) extends CosmosError

case class JsonSchemaMismatch(errors: Iterable[Json]) extends CosmosError {
  override def getData: Option[JsonObject] = Some(JsonObject.singleton("errors", errors.asJson))
}

case class UninstallNonExistentAppForPackage(packageName: String, appId: AppId) extends CosmosError

case class ServiceUnavailable(
  serviceName: String,
  causedBy: Throwable
) extends CosmosError(Some(causedBy)) {
  override val status = Status.ServiceUnavailable
  override val getData = Some(JsonObject.singleton("serviceName", serviceName.asJson))
}
case class Unauthorized(serviceName: String, realm: Option[String]) extends CosmosError with NoStackTrace {
  override val getMessage: String = s"Unable to complete request due to Unauthorized response from service [$serviceName]"
  override val status: Status = Status.Unauthorized
  override val getData: Option[JsonObject] = Some(JsonObject.singleton("serviceName", serviceName.asJson))
  override val getHeaders: Map[String, String] = realm match {
    case Some(r) =>
      Map("WWW-Authenticate" -> r)
    case _ =>
      Map.empty
  }
}
case class Forbidden(serviceName: String) extends CosmosError with NoStackTrace {
  override val getMessage: String = s"Unable to complete request due to Forbidden response from service [$serviceName]"
  override val status: Status = Status.Forbidden
  override val getData: Option[JsonObject] = Some(JsonObject.singleton("serviceName", serviceName.asJson))
}

case class IncompleteUninstall(packageName: String, causedBy: Throwable) extends CosmosError(Some(causedBy))
case class ZooKeeperStorageError(msg: String) extends CosmosError

case class ConcurrentAccess(causedBy: Throwable) extends CosmosError(Some(causedBy))

final case class RepoNameOrUriMissing() extends CosmosError

case class RepositoryAlreadyPresent(nameOrUri: Ior[String, Uri]) extends CosmosError {
  override def getData: Option[JsonObject] = {
    val jsonMap = nameOrUri match {
      case Ior.Both(n, u) => Map("name" -> n.asJson, "uri" -> u.asJson)
      case Ior.Left(n) => Map("name" -> n.asJson)
      case Ior.Right(u) => Map("uri" -> u.asJson)
    }
    Some(JsonObject.fromMap(jsonMap))
  }
}
case class RepositoryAddIndexOutOfBounds(attempted: Int, max: Int) extends CosmosError

case class UnsupportedRepositoryVersion(version: universe.v2.model.UniverseVersion) extends CosmosError
case class UnsupportedRepositoryUri(uri: Uri) extends CosmosError

case class RepositoryUriSyntax(
  repository: PackageRepository,
  causedBy: Throwable
) extends CosmosError(Some(causedBy)) {
  override def getData: Option[JsonObject] = {
    Some(JsonObject.singleton("cause", causedBy.getMessage.asJson))
  }
}

case class RepositoryUriConnection(
  repository: PackageRepository,
  causedBy: Throwable
) extends CosmosError(Some(causedBy)) {
  override def getData: Option[JsonObject] = {
    Some(JsonObject.singleton("cause", causedBy.getMessage.asJson))
  }
}

case class RepositoryNotPresent(nameOrUri: Ior[String, Uri]) extends CosmosError {
  override def getData: Option[JsonObject] = {
    val jsonMap = nameOrUri match {
      case Ior.Both(n, u) => Map("name" -> n.asJson, "uri" -> u.asJson)
      case Ior.Left(n) => Map("name" -> n.asJson)
      case Ior.Right(u) => Map("uri" -> u.asJson)
    }
    Some(JsonObject.fromMap(jsonMap))
  }
}

case class ConversionError(failure: String) extends CosmosError
case class ServiceMarathonTemplateNotFound(packageName: String, packageVersion: PackageDefinition.Version) extends CosmosError

case class EnvelopeError(msg: String) extends CosmosError

case class InstallQueueError(msg: String) extends CosmosError

case class NotImplemented(msg: String) extends CosmosError {
  override val status = Status.NotImplemented
}
// scalastyle:on number.of.types
