package com.mesosphere.cosmos.thirdparty.marathon.model

case class MarathonApp(
  id: AppId,
  labels: Map[String, String]
)

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
  val optionsLabel = "DCOS_PACKAGE_OPTIONS"
}
