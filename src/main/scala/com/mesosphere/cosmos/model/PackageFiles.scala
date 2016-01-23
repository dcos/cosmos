package com.mesosphere.cosmos.model

import cats.data.ValidatedNel
import cats.std.list._
import cats.syntax.apply._
import com.mesosphere.cosmos.{CosmosError, PackageFileSchemaMismatch, errorNel}
import com.netaporter.uri.Uri
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import io.circe.syntax._

case class PackageFiles private[cosmos] (
  version: String,
  revision: String,
  sourceUri: Uri,
  commandJson: Json,
  configJson: Json,
  marathonJsonMustache: String,
  packageJson: PackageDefinition,
  resourceJson: Resource
) {

  def describeAsJson: Json = {
    Json.obj(
      "command" -> commandJson,
      "config" -> configJson,
      "marathonTemplate" -> marathonJsonMustache.asJson,
      "package" -> packageJson.asJson,
      "resource" -> resourceJson.asJson
    )
  }
}

object PackageFiles {

  private[cosmos] def validate(
    version: String,
    revision: String,
    sourceUri: Uri,
    commandJson: Json,
    configJson: Json,
    marathonJsonMustache: String,
    packageJson: Json,
    resourceJson: Json
  ): ValidatedNel[CosmosError, PackageFiles] = {
    val packageDefValid = verifySchema[PackageDefinition](packageJson, "package.json")
    val resourceDefValid = verifySchema[Resource](resourceJson, "resource.json")

    (packageDefValid |@| resourceDefValid)
      .map { (packageDef, resourceDef) =>
        PackageFiles(
          version, revision, sourceUri, commandJson, configJson, marathonJsonMustache, packageDef, resourceDef
        )
      }
  }

  private[this] def verifySchema[A: Decoder](
    packageFileJson: Json,
    packageFileName: String
  ): ValidatedNel[CosmosError, A] = {
    packageFileJson
      .as[A]
      .leftMap(_ => errorNel(PackageFileSchemaMismatch(packageFileName)))
      .toValidated
  }

}
