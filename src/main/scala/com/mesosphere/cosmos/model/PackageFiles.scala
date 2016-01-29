package com.mesosphere.cosmos.model

import cats.data._
import cats.std.option._
import cats.syntax.traverse._
import cats.std.list._      // allows for traversU in verifySchema
import cats.syntax.apply._  // provides |@|
import cats.syntax.option._
import com.mesosphere.cosmos.{CosmosError, PackageFileSchemaMismatch}
import com.netaporter.uri.Uri
import com.mesosphere.cosmos.circe.Decoders._
import io.circe.{JsonObject, Decoder, Json}

case class PackageFiles private[cosmos](
  revision: String,
  sourceUri: Uri,
  packageJson: PackageDefinition,
  marathonJsonMustache: String,
  commandJson: Option[CommandDefinition] = None,
  configJson: Option[JsonObject] = None,
  resourceJson: Option[Resource] = None
)
object PackageFiles {

  private[cosmos] def validate(
    revision: String,
    sourceUri: Uri,
    commandJsonOpt: Option[Json],
    configJsonOption: Option[Json],
    marathonJsonMustache: String,
    packageJsonOpt: Json,
    resourceJsonOpt: Option[Json]
  ): ValidatedNel[CosmosError, PackageFiles] = {
    val packageDefValid = verifySchema[PackageDefinition](packageJsonOpt, "package.json")
    val resourceDefValid = verifySchema[Resource](resourceJsonOpt, "resource.json")
    val commandJsonValid = verifySchema[CommandDefinition](commandJsonOpt, "command.json")
    val configJsonObject = configJsonOption.traverseU {json =>
      json.asObject
        .toValidNel[CosmosError](PackageFileSchemaMismatch("config.json"))
    }

    (packageDefValid |@| resourceDefValid |@| commandJsonValid |@| configJsonObject)
      .map { (packageDef, resourceDef, commandJson, configJson) =>
        PackageFiles(
          revision,
          sourceUri,
          packageDef,
          marathonJsonMustache,
          commandJson,
          configJson,
          resourceDef
        )
      }
  }

  private[this] def verifySchema[A: Decoder](
    json: Json,
    packageFileName: String
  ): ValidatedNel[CosmosError, A] = {
    json
      .as[A]
      .leftMap(_ => errorNel(PackageFileSchemaMismatch(packageFileName)))
      .toValidated
  }

  private[this] def verifySchema[A: Decoder](
    json: Option[Json],
    packageFileName: String
  ): ValidatedNel[CosmosError, Option[A]] = {
    json
      .traverseU(verifySchema[A](_, packageFileName))
  }

  def errorNel(error: CosmosError): NonEmptyList[CosmosError] = NonEmptyList(error)

}
