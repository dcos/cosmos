package com.mesosphere.cosmos

import io.circe.Encoder

sealed trait CosmosError {

  private[cosmos] def message: String = {
    this match {
      case PackageNotFound(packageName) => s"Package [$packageName] not found"
      case EmptyPackageImport => "Package is empty"
      case PackageFileMissing(fileName) => s"Package file [$fileName] not found"
      case PackageFileNotJson(fileName, parseError) =>
        s"Package file [$fileName] is not JSON: $parseError"
      case PackageFileSchemaMismatch(fileName) => s"Package file [$fileName] does not match schema"
      case PackageAlreadyInstalled => "Package is already installed"
      case MarathonBadResponse(statusCode) =>
        s"Received response status code $statusCode from Marathon"
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
private case object EmptyPackageImport extends CosmosError
private case class PackageFileMissing(packageName: String) extends CosmosError
private case class PackageFileNotJson(fileName: String, parseError: String) extends CosmosError
private case class PackageFileSchemaMismatch(fileName: String) extends CosmosError
private case object PackageAlreadyInstalled extends CosmosError
private case class MarathonBadResponse(statusCode: Int) extends CosmosError
