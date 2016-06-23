package com.mesosphere.cosmos

import java.util.Base64

import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.netaporter.uri.Uri
import com.twitter.io.Charsets
import io.circe.{Json, JsonObject}

private[cosmos] abstract class MarathonLabels {

  protected[this] def packageMetadataJson: Json
  protected[this] def commandJson: Option[Json]
  protected[this] def packagingVersion: String
  protected[this] def packageName: String
  protected[this] def packageVersion: String
  protected[this] def sourceUri: Uri
  protected[this] def packageReleaseVersion: String
  protected[this] def isFramework: Boolean

  final def requiredLabels: Map[String, String] = {
    Map(
      (MarathonApp.metadataLabel, packageMetadata),
      (MarathonApp.registryVersionLabel, packagingVersion),
      (MarathonApp.nameLabel, packageName),
      (MarathonApp.versionLabel, packageVersion),
      (MarathonApp.repositoryLabel, sourceUri.toString),
      (MarathonApp.releaseLabel, packageReleaseVersion),
      (MarathonApp.isFrameworkLabel, isFramework.toString)
    )
  }

  final def nonOverridableLabels: Map[String, String] = {
    Seq(
      commandJson.map(command => MarathonApp.commandLabel -> encodeForLabel(command))
    ).flatten.toMap
  }

  private[this] final def packageMetadata: String = {
    encodeForLabel(removeNulls(packageMetadataJson))
  }

  private[this] final def encodeForLabel(json: Json): String = {
    val bytes = json.noSpaces.getBytes(Charsets.Utf8)
    Base64.getEncoder.encodeToString(bytes)
  }

  /** Circe populates omitted fields with null values; remove them (see GitHub issue #56) */
  private[this] final def removeNulls(json: Json): Json = {
    json.mapObject { obj =>
      JsonObject.fromMap(obj.toMap.filterNot { case (k, v) => v.isNull })
    }
  }

}
