package com.mesosphere.cosmos.render

import com.mesosphere.cosmos.bijection.CosmosConversions._
import com.mesosphere.cosmos.label.v1.circe.Encoders._
import com.mesosphere.cosmos.label.v1.model.PackageMetadata
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.mesosphere.universe.common.JsonUtil
import com.mesosphere.universe.v4.model.PackageDefinition
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import java.util.Base64

final class MarathonLabels(
  commandJson: Option[Json],
  isFramework: Boolean,
  packageMetadata: PackageMetadata,
  packageName: String,
  packageReleaseVersion: String,
  packageVersion: String,
  packagingVersion: String,
  sourceUri: Uri,
  options: Json
) {

  def requiredLabelsJson: JsonObject = JsonObject.fromMap(requiredLabels.mapValues(_.asJson))

  def requiredLabels: Map[String, String] = {
    Map(
      (MarathonApp.metadataLabel, metadataLabel),
      (MarathonApp.registryVersionLabel, packagingVersion),
      (MarathonApp.nameLabel, packageName),
      (MarathonApp.versionLabel, packageVersion),
      (MarathonApp.repositoryLabel, sourceUri.toString),
      (MarathonApp.releaseLabel, packageReleaseVersion),
      (MarathonApp.isFrameworkLabel, isFramework.toString)
    )
  }

  def nonOverridableLabelsJson: JsonObject =
    JsonObject.fromMap(nonOverridableLabels.mapValues(_.asJson))

  def nonOverridableLabels: Map[String, String] = {
    Seq(
      commandJson.map(
        command => MarathonApp.commandLabel -> MarathonLabels.encodeForLabel(command)
      ),
      Some(MarathonApp.optionsLabel -> MarathonLabels.encodeForLabel(options))
    ).flatten.toMap
  }

  private[this] def metadataLabel: String = {
    MarathonLabels.encodeForLabel(packageMetadata.asJson)
  }
}

object MarathonLabels {
  def apply(
    pkg: PackageDefinition,
    sourceUri: Uri,
    options: JsonObject
  ): MarathonLabels = {
    new MarathonLabels(
      commandJson = pkg.command.map(_.asJson(universe.v3.model.Command.encodeCommand)),
      isFramework = pkg.framework.getOrElse(false),
      packageMetadata = pkg.as[PackageMetadata],
      packageName = pkg.name,
      packageReleaseVersion = pkg.releaseVersion.value.toString,
      packageVersion = pkg.version.toString,
      packagingVersion = pkg.packagingVersion.show,
      sourceUri = sourceUri,
      options = Json.fromJsonObject(options)
    )
  }

  def encodeForLabel(json: Json): String = {
    val bytes = JsonUtil.dropNullKeysPrinter.pretty(json).getBytes(StandardCharsets.UTF_8)
    Base64.getEncoder.encodeToString(bytes)
  }
}
