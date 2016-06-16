package com.mesosphere.cosmos.thirdparty.marathon.model

import com.mesosphere.universe

case class MarathonApp(
  id: AppId,
  labels: Map[String, String],
  uris: List[String], /*TODO: uri type*/
  cpus: Double,
  mem: Double,
  instances: Int,
  cmd: Option[String],
  container: Option[MarathonAppContainer]
) {
  def packageName: Option[String] = labels.get(MarathonApp.nameLabel)

  def packageReleaseVersion: Option[universe.v3.model.PackageDefinition.ReleaseVersion] = {
    // TODO(version): This can throw
    labels.get(MarathonApp.releaseLabel)
      .map(_.toInt)
      .map(universe.v3.model.PackageDefinition.ReleaseVersion(_))
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
