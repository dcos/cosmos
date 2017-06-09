package com.mesosphere.cosmos

import _root_.io.circe.Encoder
import _root_.io.circe.Json
import _root_.io.circe.JsonObject
import _root_.io.circe.generic.semiauto.deriveEncoder
import _root_.io.circe.syntax._
import cats.data.Ior
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.CosmosError
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import com.mesosphere.universe.common.circe.Encoders._
import com.mesosphere.universe.v2.circe.Encoders._
import com.mesosphere.util.PackageUtil
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import org.jboss.netty.handler.codec.http.HttpMethod


final case class MarathonGenericError(marathonStatus: Status) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Received response status code ${marathonStatus.code} from Marathon"
  }

  override def exception: CosmosException = {
    exception(Status.InternalServerError, Map.empty, None)
  }
}

object MarathonGenericError {
  implicit val encoder: Encoder[MarathonGenericError] = deriveEncoder
}

final case class MarathonBadGateway(marathonStatus: Status) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Received response status code ${marathonStatus.code} from Marathon"
  }

  override def exception: CosmosException = {
    exception(Status.BadGateway, Map.empty, None)
  }
}

object MarathonBadGateway {
  implicit val encoder: Encoder[MarathonBadGateway] = deriveEncoder
}


final case class IndexNotFound(repoUri: Uri) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Index file missing for repo [$repoUri]"
}

object IndexNotFound {
  implicit val encoder: Encoder[IndexNotFound] = deriveEncoder
}


final case class MarathonAppDeleteError(appId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Error while deleting marathon app '$appId'"
}

object MarathonAppDeleteError {
  implicit val encoder: Encoder[MarathonAppDeleteError] = deriveEncoder
}

final case class MarathonAppNotFound(appId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Unable to locate service with marathon appId: '$appId'"
}

object MarathonAppNotFound {
  implicit val encoder: Encoder[MarathonAppNotFound] = deriveEncoder
}

// TODO: This doesn't look right. Doesn't look like very useful exception
// TODO: Doesn't look like we need the full circeError
final case class CirceError(circeError: _root_.io.circe.Error) extends CosmosError {
  override val data = None
  override def message: String = circeError.getMessage
}

final case class AppAlreadyUninstalling(appId: AppId) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"A request to uninstall the service '$appId' is already in progress."
  }

  override def exception: CosmosException = {
    exception(Status.Conflict, Map.empty, None)
  }
}

object AppAlreadyUninstalling {
  implicit val encoder: Encoder[AppAlreadyUninstalling] = deriveEncoder
}

final case class FailedToStartUninstall(appId: AppId, explanation: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Failed to start an uninstall for the service '$appId': $explanation"
  }
}

object FailedToStartUninstall {
  implicit val encoder: Encoder[FailedToStartUninstall] = deriveEncoder
}


case object MarathonTemplateMustBeJsonObject extends CosmosError {
  override val data = None
  override def message: String = "Rendered Marathon JSON must be a JSON object"
}

final case class UnsupportedContentType(
  supported: List[MediaType],
  actual: Option[String] = None
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    val acceptMsg = supported.map(_.show).mkString("[", ", ", "]")
    actual match {
      case Some(mt) =>
        s"Unsupported Content-Type: $mt Accept: $acceptMsg"
      case None =>
        s"Unspecified Content-Type Accept: $acceptMsg"
    }
  }
}

object UnsupportedContentType {
  def forMediaType(
    supported: List[MediaType],
    actual: Option[MediaType]
  ): UnsupportedContentType = {
    new UnsupportedContentType(supported, actual.map(_.show))
  }

  implicit val encoder: Encoder[UnsupportedContentType] = deriveEncoder
}


final case class UnsupportedContentEncoding(
  supported: List[String],
  actual: Option[String] = None
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    val acceptMsg = supported.mkString("[", ", ", "]")
    actual match {
      case Some(mt) =>
        s"Unsupported Content-Encoding: $mt Accept-Encoding: $acceptMsg"
      case None =>
        s"Unspecified Content-Encoding Accept-Encoding: $acceptMsg"
    }
  }
}

object UnsupportedContentEncoding {
  implicit val encoder: Encoder[UnsupportedContentEncoding] = deriveEncoder
}

final case class UnsupportedRedirect(
  supported: List[String],
  actual: Option[String] = None
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    val supportedMsg = supported.mkString("[", ", ", "]")
    actual match {
      case Some(act) =>
        s"Unsupported redirect scheme - supported: $supportedMsg actual: $act"
      case None =>
        s"Unsupported redirect scheme - supported: $supportedMsg"
    }
  }
}

object UnsupportedRedirect {
  implicit val encoder: Encoder[UnsupportedRedirect] = deriveEncoder
}

final case class GenericHttpError(
  method: HttpMethod,
  uri: Uri,
  clientStatus: Status
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Unexpected down stream http error: ${method.getName} ${uri.toString} ${clientStatus.code}"
  }

  def exception(status: Status): CosmosException = {
    exception(status, Map.empty, None)
  }
}

object GenericHttpError {
  implicit val encoder: Encoder[GenericHttpError] = deriveEncoder
}

final case class AmbiguousAppId(packageName: String, appIds: List[AppId]) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Multiple apps named [$packageName] are installed: [${appIds.mkString(", ")}]"
  }
}

object AmbiguousAppId {
  implicit val encoder: Encoder[AmbiguousAppId] = deriveEncoder
}

final case class MultipleFrameworkIds(
  packageName: String,
  packageVersion: Option[universe.v2.model.PackageDetailsVersion],
  frameworkName: String,
  ids: List[String]
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    packageVersion match {
      case Some(ver) =>
        s"Uninstalled package [$packageName] version [$ver]\n" +
        s"Unable to shutdown [$packageName] service framework with name [$frameworkName] " +
        s"because there are multiple framework ids matching this name: [${ids.mkString(", ")}]"
      case None =>
        s"Uninstalled package [$packageName]\n" +
        s"Unable to shutdown [$packageName] service framework with name [$frameworkName] " +
        s"because there are multiple framework ids matching this name: [${ids.mkString(", ")}]"
    }
  }
}

object MultipleFrameworkIds {
  implicit val encoder: Encoder[MultipleFrameworkIds] = deriveEncoder
}

final case class PackageNotInstalled(packageName: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Package [$packageName] is not installed"
}

object PackageNotInstalled {
  implicit val encoder: Encoder[PackageNotInstalled] = deriveEncoder
}

final case class JsonSchemaMismatch(errors: Iterable[Json]) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = "Options JSON failed validation"
}

object JsonSchemaMismatch {
  implicit val encoder: Encoder[JsonSchemaMismatch] = deriveEncoder
}

final case class UninstallNonExistentAppForPackage(
  packageName: String,
  appId: AppId
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Package [$packageName] with id [$appId] is not installed"
}

object UninstallNonExistentAppForPackage {
  implicit val encoder: Encoder[UninstallNonExistentAppForPackage] = deriveEncoder
}


// TODO: move Throwable to CosmosException: causedBy: Throwable
final case class ServiceUnavailable(
  serviceName: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Unable to complete request due to service [$serviceName] unavailability"
  }

  override def exception: CosmosException = {
    exception(Status.ServiceUnavailable, Map.empty, None)
  }
}

object ServiceUnavailable {
  implicit val encoder: Encoder[ServiceUnavailable] = deriveEncoder
}


final case class Unauthorized(
  serviceName: String,
  realm: Option[String]
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override val message: String = {
    s"Unable to complete request due to Unauthorized response from service [$serviceName]"
  }

  override def exception: CosmosException = {
    exception(
      Status.Unauthorized,
      realm.map(r => Map("WWW-Authenticate" -> r)).getOrElse(Map.empty),
      None
    )
  }
}

object Unauthorized {
  implicit val encoder: Encoder[Unauthorized] = deriveEncoder
}


final case class Forbidden(serviceName: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Unable to complete request due to Forbidden response from service [$serviceName]"
  }

  override def exception: CosmosException = {
    exception(Status.Forbidden, Map.empty, None)
  }
}

object Forbidden {
  implicit val encoder: Encoder[Forbidden] = deriveEncoder
}


// TODO: Move this to CosmosException: causedBy: Throwable
final case class IncompleteUninstall(
  packageName: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Incomplete uninstall of package [$packageName] due to Mesos unavailability"
  }
}

object IncompleteUninstall {
  implicit val encoder: Encoder[IncompleteUninstall] = deriveEncoder
}


final case class ZooKeeperStorageError(override val message: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
}

object ZooKeeperStorageError {
  implicit val encoder: Encoder[ZooKeeperStorageError] = deriveEncoder
}


// TODO: Move this to CosmosException: causedBy: Throwable
final case class ConcurrentAccess() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = {
    s"Retry operation. Operation didn't complete due to concurrent access."
  }
}


final case class RepoNameOrUriMissing() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = s"Must specify either the name or URI of the repository"
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
  override def message: String = {
    nameOrUri match {
      case Ior.Both(n, u) =>
        s"Repository name [$n] and URI [$u] are both already present in the list"
      case Ior.Left(n) => s"Repository name [$n] is already present in the list"
      case Ior.Right(u) => s"Repository URI [$u] is already present in the list"
    }
  }
}


final case class RepositoryAddIndexOutOfBounds(attempted: Int, max: Int) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Index out of range: $attempted"
}

object RepositoryAddIndexOutOfBounds {
  implicit val encoder: Encoder[RepositoryAddIndexOutOfBounds] = deriveEncoder
}

final case class UnsupportedRepositoryVersion(
  version: universe.v2.model.UniverseVersion
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = s"Repository version [$version] is not supported"
}

object UnsupportedRepositoryVersion {
  implicit val encoder: Encoder[UnsupportedRepositoryVersion] = deriveEncoder
}

final case class UnsupportedRepositoryUri(uri: Uri) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Repository URI [$uri] uses an unsupported scheme. Only http and https are supported"
  }
}

object UnsupportedRepositoryUri {
  implicit val encoder: Encoder[UnsupportedRepositoryUri] = deriveEncoder
}

// TODO: fix this cosmos error: causedBy: Throwable
// TODO: cause is suppose to be: causedBy.getMessage
final case class RepositoryUriSyntax(
  repository: rpc.v1.model.PackageRepository,
  cause: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"URI for repository [${repository.name}] has invalid syntax: ${repository.uri}"
  }
}

object RepositoryUriSyntax {
  implicit val encoder: Encoder[RepositoryUriSyntax] = deriveEncoder
}

// TODO: fix this cosmos error: causedBy: Throwable
// TODO: cause is suppose to be: causedBy.getMessage
final case class RepositoryUriConnection(
  repository: rpc.v1.model.PackageRepository,
  cause: String
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Could not access data at URI for repository [${repository.name}]: ${repository.uri}"
  }
}

object RepositoryUriConnection {
  implicit val encoder: Encoder[RepositoryUriConnection] = deriveEncoder
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
  override def message: String = {
    nameOrUri match {
      case Ior.Both(n, u) => s"Neither repository name [$n] nor URI [$u] are present in the list"
      case Ior.Left(n) => s"Repository name [$n] is not present in the list"
      case Ior.Right(u) => s"Repository URI [$u] is not present in the list"
    }
  }
}


final case class ConversionError(failure: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = failure
}

object ConversionError {
  implicit val encoder: Encoder[ConversionError] = deriveEncoder
}


final case class ServiceMarathonTemplateNotFound(
  packageName: String,
  packageVersion: universe.v3.model.Version
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Package: [$packageName] version: [$packageVersion] does not have a Marathon " +
    "template defined and can not be rendered"
  }
}

object ServiceMarathonTemplateNotFound {
  implicit val encoder: Encoder[ServiceMarathonTemplateNotFound] = deriveEncoder
}


final case class InstallQueueError(override val message: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
}

object InstallQueueError {
  implicit val encoder: Encoder[InstallQueueError] = deriveEncoder
}


final case class NotImplemented(override val message: String) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)

  override def exception: CosmosException = {
    exception(Status.NotImplemented, Map.empty, None)
  }
}

object NotImplemented {
  implicit val encoder: Encoder[NotImplemented] = deriveEncoder
}

final case class OperationInProgress(coordinate: rpc.v1.model.PackageCoordinate) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"A change to package ${coordinate.name}-${coordinate.version} is already in progress"
  }

  override def exception: CosmosException = {
    exception(Status.Conflict, Map.empty, None)
  }
}

object OperationInProgress {
  implicit val encoder: Encoder[OperationInProgress] = deriveEncoder
}

final case class ConversionFromPackageToV1AddResponse() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = "A v4 package cannot be converted into a v1 AddResponse"
}


final case class ConversionFromPackageToV2DescribeResponse() extends CosmosError {
  override def data: Option[JsonObject] = None
  override def message: String = "A v4 package cannot be converted into a v2 DescribeResponse"
}


final case class OptionsNotStored(
  override val message: String
) extends CosmosError {
  override def data: Option[JsonObject] = None
}


final case class AppIdChanged(
  override val message: String
) extends CosmosError {
  override def data: Option[JsonObject] = None
}


final case class OptionsConflict(
  override val message: String
) extends CosmosError {
  override def data: Option[JsonObject] = None
}


final case class BadVersionUpdate(
  currentVersion: universe.v3.model.Version,
  updateVersion: universe.v3.model.Version,
  validVersions: List[universe.v3.model.Version]
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"The service of version $currentVersion cannot update to the requested " +
    s"version $updateVersion"
  }
}

object BadVersionUpdate {
  implicit val encoder: Encoder[BadVersionUpdate] = deriveEncoder
}


final case class ServiceUpdateError(
  override val message: String
) extends CosmosError {
  override def data: Option[JsonObject] = None
}

// scalastyle:on number.of.types
