package com.mesosphere.cosmos.model

case class InstallRequest(name: String, version: Option[String] = None)
