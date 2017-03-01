package com.mesosphere.cosmos

import _root_.io.circe.DecodingFailure
import _root_.io.circe.Json
import _root_.io.circe.JsonObject
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
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.util.PackageUtil
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import org.jboss.netty.handler.codec.http.HttpMethod
import scala.util.control.NoStackTrace

// scalastyle:off number.of.types
sealed abstract class CosmosError(
  causedBy: Option[Throwable] = None
) extends RuntimeException(causedBy.orNull) {

  final val errType: String = this.getClass.getSimpleName

  val status: Status = Status.BadRequest

  val getHeaders: Map[String, String] = Map.empty

  val getData: Option[JsonObject]
}

case class InvalidPackageVersionForAdd(
  packageCoordinate: rpc.v1.model.PackageCoordinate
) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map(
          "packageName" -> packageCoordinate.name.asJson,
          "packageVersion" -> packageCoordinate.version.asJson
        )
      )
    )
  }
}

case class PackageNotFound(packageName: String) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map("packageName" -> packageName.asJson)
      )
    )
  }
}

case class VersionNotFound(
  packageName: String,
  packageVersion: universe.v3.model.PackageDefinition.Version
) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map(
          "packageName" -> packageName.asJson,
          "packageVersion" -> packageVersion.asJson
        )
      )
    )
  }
}

case class PackageFileMissing(
  packageName: String,
  cause: Option[Throwable] = None
) extends CosmosError(cause) {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map("packageName" -> packageName.asJson)
      )
    )
  }
}

case class PackageFileNotJson(fileName: String, parseError: String) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map(
          "fileName" -> fileName.asJson,
          "parseError" -> parseError.asJson
        )
      )
    )
  }
}

case class UnableToParseMarathonAsJson(parseError: String) extends  CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map("parseError" -> parseError.asJson)
      )
    )
  }
}

case class PackageFileSchemaMismatch(fileName: String, decodingFailure: DecodingFailure) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("errorMessage", decodingFailure.getMessage().asJson))
  }
}

case class PackageAlreadyInstalled() extends CosmosError {
  override val status = Status.Conflict
  override val getData = None
}

case class ServiceAlreadyStarted() extends CosmosError {
  override val status = Status.Conflict
  override val getData = None
}

case class MarathonBadResponse(marathonError: MarathonError) extends CosmosError {
  override val getData: Option[JsonObject] = {
    marathonError.details.map(details => JsonObject.singleton("errors", details.asJson))
  }
}

case class MarathonGenericError(marathonStatus: Status) extends CosmosError {
  override val status = Status.InternalServerError
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map("marathonStatus" -> marathonStatus.asJson)
      )
    )
  }
}

case class MarathonBadGateway(marathonStatus: Status) extends CosmosError {
  override val status = Status.BadGateway
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map("marathonStatus" -> marathonStatus.asJson)
      )
    )
  }
}

case class IndexNotFound(repoUri: Uri) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map("repoUri" -> repoUri.asJson)
      )
    )
  }
}


case class MarathonAppDeleteError(appId: AppId) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map("appId" -> appId.asJson)
      )
    )
  }
}

case class MarathonAppNotFound(appId: AppId) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map("appId" -> appId.asJson)
      )
    )
  }
}

case class CirceError(circeError: _root_.io.circe.Error) extends CosmosError {
  override val getData = None
}

case object MarathonTemplateMustBeJsonObject extends CosmosError {
  override val getData = None
}

case class UnsupportedContentType(supported: List[MediaType], actual: Option[String] = None) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(JsonObject.fromMap(Map(
      "supported" -> supported.asJson,
      "actual" -> actual.asJson
    )))
  }
}

object UnsupportedContentType {
  def forMediaType(
    supported: List[MediaType],
    actual: Option[MediaType]
  ): UnsupportedContentType = {
    new UnsupportedContentType(supported, actual.map(_.show))
  }
}

case class UnsupportedContentEncoding(supported: List[String], actual: Option[String] = None) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(JsonObject.fromMap(Map(
      "supported" -> supported.asJson,
      "actual" -> actual.asJson
    )))
  }
}

case class UnsupportedRedirect(supported: List[String], actual: Option[String] = None) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(JsonObject.fromMap(Map(
      "supported" -> supported.asJson,
      "actual" -> actual.asJson
    )))
  }
}

case class GenericHttpError(method: HttpMethod, uri: Uri, override val status: Status) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map(
          "method" -> method.asJson,
          "uri" -> uri.asJson,
          "status" -> status.asJson
        )
      )
    )
  }
}

object GenericHttpError {
  def apply(method: String, uri: Uri, status: Int): GenericHttpError = {
    new GenericHttpError(HttpMethod.valueOf(method.toUpperCase), uri, Status.fromCode(status))
  }
}

case class AmbiguousAppId(packageName: String, appIds: List[AppId]) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map(
          "packageName" -> packageName.asJson,
          "appIds" -> appIds.asJson
        )
      )
    )
  }
}

case class MultipleFrameworkIds(
  packageName: String,
  packageVersion: Option[universe.v2.model.PackageDetailsVersion],
  frameworkName: String,
  ids: List[String]
) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map(
          "packageName" -> packageName.asJson,
          "packageVersion" -> packageVersion.asJson,
          "frameworkName" -> frameworkName.asJson,
          "ids" -> ids.asJson
        )
      )
    )
  }
}

case class PackageNotInstalled(packageName: String) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map("packageName" -> packageName.asJson)
      )
    )
  }
}

case class JsonSchemaMismatch(errors: Iterable[Json]) extends CosmosError {
  override val getData: Option[JsonObject] = Some(JsonObject.singleton("errors", errors.asJson))
}

case class UninstallNonExistentAppForPackage(
  packageName: String,
  appId: AppId
) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map(
          "packageName" -> packageName.asJson,
          "appId" -> appId.asJson
        )
      )
    )
  }
}

case class ServiceUnavailable(
  serviceName: String,
  causedBy: Throwable
) extends CosmosError(Some(causedBy)) {
  override val status = Status.ServiceUnavailable
  override val getData = Some(JsonObject.singleton("serviceName", serviceName.asJson))
}

case class Unauthorized(
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

case class Forbidden(serviceName: String) extends CosmosError with NoStackTrace {
  override val getMessage: String = {
    s"Unable to complete request due to Forbidden response from service [$serviceName]"
  }

  override val status: Status = Status.Forbidden

  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("serviceName", serviceName.asJson))
  }
}

case class IncompleteUninstall(
  packageName: String,
  causedBy: Throwable
) extends CosmosError(Some(causedBy)) {
  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("packageName", packageName.asJson))
  }
}

case class ZooKeeperStorageError(msg: String) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("msg", msg.asJson))
  }
}

case class ConcurrentAccess(causedBy: Throwable) extends CosmosError(Some(causedBy)) {
  override val getData = None
}

final case class RepoNameOrUriMissing() extends CosmosError {
  override val getData = None
}

case class RepositoryAlreadyPresent(nameOrUri: Ior[String, Uri]) extends CosmosError {
  override val getData: Option[JsonObject] = {
    val jsonMap = nameOrUri match {
      case Ior.Both(n, u) => Map("name" -> n.asJson, "uri" -> u.asJson)
      case Ior.Left(n) => Map("name" -> n.asJson)
      case Ior.Right(u) => Map("uri" -> u.asJson)
    }
    Some(JsonObject.fromMap(jsonMap))
  }
}

case class RepositoryAddIndexOutOfBounds(attempted: Int, max: Int) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map(
          "attempted" -> attempted.asJson,
          "max" -> max.asJson
        )
      )
    )
  }
}

case class UnsupportedRepositoryVersion(
  version: universe.v2.model.UniverseVersion
) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map("version" -> version.asJson)
      )
    )
  }
}

case class UnsupportedRepositoryUri(uri: Uri) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map("uri" -> uri.asJson)
      )
    )
  }
}

case class RepositoryUriSyntax(
  repository: rpc.v1.model.PackageRepository,
  causedBy: Throwable
) extends CosmosError(Some(causedBy)) {
  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("cause", causedBy.getMessage.asJson))
  }
}

case class RepositoryUriConnection(
  repository: rpc.v1.model.PackageRepository,
  causedBy: Throwable
) extends CosmosError(Some(causedBy)) {
  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("cause", causedBy.getMessage.asJson))
  }
}

case class RepositoryNotPresent(nameOrUri: Ior[String, Uri]) extends CosmosError {
  override val getData: Option[JsonObject] = {
    val jsonMap = nameOrUri match {
      case Ior.Both(n, u) => Map("name" -> n.asJson, "uri" -> u.asJson)
      case Ior.Left(n) => Map("name" -> n.asJson)
      case Ior.Right(u) => Map("uri" -> u.asJson)
    }
    Some(JsonObject.fromMap(jsonMap))
  }
}

case class ConversionError(failure: String) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("failure", failure.asJson))
  }
}

case class ServiceMarathonTemplateNotFound(
  packageName: String,
  packageVersion: universe.v3.model.PackageDefinition.Version
) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(
      JsonObject.fromMap(
        Map(
          "packageName" -> packageName.asJson,
          "packageVersion" -> packageVersion.asJson
        )
      )
    )
  }
}

case class EnvelopeError(msg: String) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("msg", msg.asJson))
  }
}

case class InstallQueueError(msg: String) extends CosmosError {
  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("msg", msg.asJson))
  }
}

case class NotImplemented(msg: String) extends CosmosError {
  override val status = Status.NotImplemented

  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("msg", msg.asJson))
  }
}

case class OperationInProgress(coordinate: rpc.v1.model.PackageCoordinate) extends CosmosError {
  override val status = Status.Conflict

  override val getData: Option[JsonObject] = {
    Some(JsonObject.singleton("coordinate", coordinate.asJson))
  }
}

case class InvalidPackage(reason: PackageUtil.PackageError) extends CosmosError(Some(reason)) {
  override val getData: Option[JsonObject] = Some(reason.getData)
}
// scalastyle:on number.of.types
