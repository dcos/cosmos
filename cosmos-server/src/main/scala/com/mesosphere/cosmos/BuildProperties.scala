package com.mesosphere.cosmos

import java.util.Properties

private[cosmos] class BuildProperties private[cosmos](resourceName: String) {
  private val props = {
    val props = new Properties()
    Option(this.getClass.getResourceAsStream(resourceName)) match {
      case Some(is) =>
        props.load(is)
        is.close()
        props
      case _ =>
        throw new IllegalStateException(s"Unable to load classpath resources: $resourceName")
    }
  }
}

object BuildProperties {
  private[this] val loaded = new BuildProperties("/build.properties")

  def apply(): BuildProperties = loaded

  implicit class BuildPropertiesOps(val bp: BuildProperties) {

    val cosmosVersion: String = Option(bp.props.getProperty("cosmos.version")).getOrElse("unknown-version")

  }

}
