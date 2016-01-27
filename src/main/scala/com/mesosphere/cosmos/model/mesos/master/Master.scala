package com.mesosphere.cosmos.model.mesos.master

import com.mesosphere.cosmos.model.AppId

//todo: flush out
case class MasterState(
  frameworks: List[Framework]
)

case class Framework(
  id: String,
  name: String
)

case class MesosFrameworkTearDownResponse()

case class MarathonAppResponse(app: MarathonApp)
case class MarathonAppsResponse(apps: List[MarathonApp])

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

  def packageReleaseVersion: Option[String] = labels.get(MarathonApp.releaseLabel)

  def packageVersion: Option[String] = labels.get(MarathonApp.versionLabel)

  def packageSource: Option[String] = labels.get(MarathonApp.sourceLabel)
}

object MarathonApp {
  val frameworkNameLabel = "DCOS_PACKAGE_FRAMEWORK_NAME"
  val isFrameworkLabel = "DCOS_PACKAGE_IS_FRAMEWORK"
  val metadataLabel = "DCOS_PACKAGE_METADATA"
  val nameLabel = "DCOS_PACKAGE_NAME"
  val registryVersionLabel = "DCOS_PACKAGE_REGISTRY_VERSION"
  val releaseLabel = "DCOS_PACKAGE_RELEASE"
  val sourceLabel = "DCOS_PACKAGE_SOURCE"
  val versionLabel = "DCOS_PACKAGE_VERSION"
  val commandLabel = "DCOS_PACKAGE_COMMAND"
}

case class MarathonAppContainer(`type`: String, docker: Option[MarathonAppContainerDocker])

case class MarathonAppContainerDocker(image: String, network: String)
