package com.mesosphere.cosmos

import java.nio.charset.StandardCharsets
import java.util.Base64

import com.mesosphere.cosmos.converter.Label._
import com.mesosphere.cosmos.label.v1.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import io.circe.{Json, JsonObject}
import io.circe.syntax._

final class MarathonLabels(
  commandJson: Option[Json],
  isFramework: Boolean,
  packageMetadata: label.v1.model.PackageMetadata,
  packageName: String,
  packageReleaseVersion: String,
  packageVersion: String,
  packagingVersion: String,
  sourceUri: Uri
) {

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

  def nonOverridableLabels: Map[String, String] = {
    Seq(
      commandJson.map(command => MarathonApp.commandLabel -> encodeForLabel(command))
    ).flatten.toMap
  }

  private[this] def metadataLabel: String = {
    encodeForLabel(removeNulls(packageMetadata.asJson))
  }

  private[this] def encodeForLabel(json: Json): String = {
    val bytes = json.noSpaces.getBytes(StandardCharsets.UTF_8)
    Base64.getEncoder.encodeToString(bytes)
  }

  /** Circe populates omitted fields with null values; remove them (see GitHub issue #56) */
  private[this] def removeNulls(json: Json): Json = {
    json.mapObject { obj =>
      JsonObject.fromMap(obj.toMap.filterNot { case (k, v) => v.isNull })
    }
  }

}

object MarathonLabels {

  def apply(pkg: internal.model.PackageDefinition, sourceUri: Uri): MarathonLabels = {
    new MarathonLabels(
      commandJson = pkg.command.map(_.asJson(universe.v3.circe.Encoders.encodeCommand)),
      isFramework = pkg.framework,
      packageMetadata = pkg.as[label.v1.model.PackageMetadata],
      packageName = pkg.name,
      packageReleaseVersion = pkg.releaseVersion.value.toString,
      packageVersion = pkg.version.toString,
      packagingVersion = pkg.packagingVersion.show,
      sourceUri = sourceUri
    )
  }

}
