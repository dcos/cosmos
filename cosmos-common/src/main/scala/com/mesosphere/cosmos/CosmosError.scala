package com.mesosphere.cosmos

import _root_.io.circe.Encoder
import _root_.io.circe.Json
import _root_.io.circe.JsonObject
import _root_.io.circe.generic.semiauto.deriveEncoder
import _root_.io.circe.syntax._
import cats.data.Ior
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.CosmosError
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.universe
import com.mesosphere.universe.common.circe.Encoders._
import com.mesosphere.universe.v2.circe.Encoders._
import com.mesosphere.util.PackageUtil
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import org.jboss.netty.handler.codec.http.HttpMethod

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
