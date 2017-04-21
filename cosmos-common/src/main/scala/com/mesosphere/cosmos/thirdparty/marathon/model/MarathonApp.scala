package com.mesosphere.cosmos.thirdparty.marathon.model

/** Partial Marathon AppDefinition.
 *
 *  This is not a full Marathon AppDefinition. Marathon's AppDefinition is a moving target.
 *  We should only decode the parts that Cosmos cares about. That is the `id` and the `labels`.
 *  This is okay as long as we don't have an encoder for this class.
 */
case class MarathonApp(
  id: AppId,
  labels: Map[String, String]
)

object MarathonApp {
  val frameworkNameLabel = "DCOS_PACKAGE_FRAMEWORK_NAME"
  val metadataLabel = "DCOS_PACKAGE_METADATA"
  val nameLabel = "DCOS_PACKAGE_NAME"
  val repositoryLabel = "DCOS_PACKAGE_SOURCE"
  val versionLabel = "DCOS_PACKAGE_VERSION"
  val optionsLabel = "DCOS_PACKAGE_OPTIONS"
  val packageLabel = "DCOS_PACKAGE_DEFINITION"
}
