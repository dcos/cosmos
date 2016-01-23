package com.mesosphere.cosmos

import cats.data.NonEmptyList
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import io.circe.Encoder

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
      case IndexNotFound() => s"Index file missing for repo [${universeBundleUri()}]"
      case MarathonAppMetadataError(note) => note
      case MarathonAppDeleteError(appId) => s"Error while deleting marathon app '$appId'"
      case MarathonAppNotFound(appId) => s"Unable to locate service with marathon appId: '$appId'"
      case CirceError(cerr) => cerr.getMessage
      case MesosRequestError(note) => note
      case uct @ UnsupportedContentType(_, _) => uct.toString
      case ghe @ GenericHttpError(uri, status) => ghe.toString
      case aai @ AmbiguousAppId(_, _) => aai.toString
      case mfi @ MultipleFrameworkIds(_, _) => mfi.toString
      case me @ MultipleError(_) => me.toString
      case nelE @ NelErrors(_) => nelE.toString
    }
  }

}

object CosmosError {

  implicit val jsonEncoder: Encoder[CosmosError] = {
    Encoder[Map[String, List[Map[String, String]]]].contramap { error =>
      Map("errors" -> List(
        Map("message" -> error.getMessage)
      ))
    }
  }

}

case class PackageNotFound(packageName: String) extends CosmosError
case class VersionNotFound(packageName: String, packageVersion: String) extends CosmosError
case class EmptyPackageImport() extends CosmosError
case class PackageFileMissing(packageName: String) extends CosmosError
case class PackageFileNotJson(fileName: String, parseError: String) extends CosmosError
case class PackageFileSchemaMismatch(fileName: String) extends CosmosError
case class PackageAlreadyInstalled() extends CosmosError { override val status = Status.Conflict }
case class MarathonBadResponse(marathonStatus: Status) extends CosmosError { override val status = Status.InternalServerError }
case class MarathonBadGateway(marathonStatus: Status) extends CosmosError { override val status = Status.BadGateway }
case class IndexNotFound() extends CosmosError

case class MarathonAppMetadataError(note: String) extends CosmosError
case class MarathonAppDeleteError(appId: String) extends CosmosError
case class MarathonAppNotFound(appId: String) extends CosmosError
case class MesosRequestError(note: String) extends CosmosError
case class CirceError(cerr: io.circe.Error) extends CosmosError

case class UnsupportedContentType(contentType: Option[String], supported: String) extends CosmosError

case class GenericHttpError(uri: Uri, override val status: Status) extends CosmosError

case class AmbiguousAppId(packageName: String, appIds: List[String]) extends CosmosError
case class MultipleFrameworkIds(frameworkName: String, ids: List[String]) extends CosmosError

case class MultipleError(errs: List[CosmosError]) extends CosmosError
case class NelErrors(errs: NonEmptyList[CosmosError]) extends CosmosError //TODO: Cleanup
