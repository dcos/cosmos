package com.mesosphere.cosmos.thirdparty.marathon.model

import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod

import scala.util.Try

case class MarathonApp(
  id: AppId,
  labels: Map[String, String],
  uris: List[String],
  cpus: Double,
  mem: Double,
  instances: Int,
  cmd: Option[String],
  container: Option[MarathonAppContainer]
) {
  def packageName: Option[String] = labels.get(MarathonApp.nameLabel)

  def packageReleaseVersion: Option[universe.v3.model.PackageDefinition.ReleaseVersion] = {
    labels.get(MarathonApp.releaseLabel).flatMap { label =>
      label.as[Try[universe.v3.model.PackageDefinition.ReleaseVersion]].toOption
    }
  }

  def packageVersion: Option[universe.v3.model.PackageDefinition.Version] = {
    labels.get(MarathonApp.versionLabel).map(universe.v3.model.PackageDefinition.Version)
  }

  def packageRepository: Option[String] = labels.get(MarathonApp.repositoryLabel)

  def packageMetadata: Option[String] = labels.get(MarathonApp.metadataLabel)
}

object MarathonApp {
  val frameworkNameLabel = "DCOS_PACKAGE_FRAMEWORK_NAME"
  val isFrameworkLabel = "DCOS_PACKAGE_IS_FRAMEWORK"
  val metadataLabel = "DCOS_PACKAGE_METADATA"
  val nameLabel = "DCOS_PACKAGE_NAME"
  val registryVersionLabel = "DCOS_PACKAGE_REGISTRY_VERSION"
  val releaseLabel = "DCOS_PACKAGE_RELEASE"
  val repositoryLabel = "DCOS_PACKAGE_SOURCE"
  val versionLabel = "DCOS_PACKAGE_VERSION"
  val commandLabel = "DCOS_PACKAGE_COMMAND"
}
