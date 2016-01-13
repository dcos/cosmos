package com.mesosphere.cosmos

import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import io.circe.Encoder

sealed trait CosmosError {

  private[cosmos] def message: String = {
    this match {
      case PackageNotFound(packageName) => s"Package [$packageName] not found"
      case VersionNotFound(packageName, packageVersion) =>
        s"Version [$packageVersion] of package [$packageName] not found"
      case EmptyPackageImport => "Package is empty"
      case PackageFileMissing(fileName) => s"Package file [$fileName] not found"
      case PackageFileNotJson(fileName, parseError) =>
        s"Package file [$fileName] is not JSON: $parseError"
      case PackageFileSchemaMismatch(fileName) => s"Package file [$fileName] does not match schema"
      case PackageAlreadyInstalled => "Package is already installed"
      case MarathonBadResponse(statusCode) =>
        s"Received response status code $statusCode from Marathon"
      case IndexNotFound => s"Index file missing for repo [${universeBundleUri()}]"
      case MarathonAppMetadataError(note) => note
      case MarathonAppDeleteError(appId) => s"Error while deleting marathon app '$appId'"
      case MarathonAppNotFound(appId) => s"Unable to locate service with marathon appId: '$appId'"
      case CirceError(cerr) => cerr.getMessage
      case MesosRequestError(note) => note
      case ce @ ClientError(s, note) => ce.toString
      case se @ ServerError(s, note) => se.toString
      case uct @ UnsupportedContentType(_, _) => uct.toString
      case ghe @ GenericHttpError(uri, status) => ghe.toString
      case aai @ AmbiguousAppId(_, _) => aai.toString
      case mfi @ MultipleFrameworkIds(_, _) => mfi.toString
    }
  }

}

object CosmosError {

  implicit val jsonEncoder: Encoder[CosmosError] = {
    Encoder[Map[String, String]].contramap { error =>
      Map("message" -> error.message)
    }
  }

}

private case class PackageNotFound(packageName: String) extends CosmosError
private case class VersionNotFound(packageName: String, packageVersion: String) extends CosmosError
private case object EmptyPackageImport extends CosmosError
private case class PackageFileMissing(packageName: String) extends CosmosError
private case class PackageFileNotJson(fileName: String, parseError: String) extends CosmosError
private case class PackageFileSchemaMismatch(fileName: String) extends CosmosError
private case object PackageAlreadyInstalled extends CosmosError
private case class MarathonBadResponse(statusCode: Int) extends CosmosError
private case object IndexNotFound extends CosmosError

private case class MarathonAppMetadataError(note: String) extends CosmosError
private case class MarathonAppDeleteError(appId: String) extends CosmosError
private case class MarathonAppNotFound(appId: String) extends CosmosError
private case class MesosRequestError(note: String) extends CosmosError
private case class CirceError(cerr: io.circe.Error) extends CosmosError

private case class ClientError(status: Int, note: String) extends CosmosError
private case class ServerError(status: Int, note: String) extends CosmosError
private case class UnsupportedContentType(contentType: Option[String], supported: String) extends CosmosError

private case class GenericHttpError(uri: Uri, status: Status) extends CosmosError

private case class AmbiguousAppId(packageName: String, appIds: List[String]) extends CosmosError
private case class MultipleFrameworkIds(frameworkName: String, ids: List[String]) extends CosmosError
