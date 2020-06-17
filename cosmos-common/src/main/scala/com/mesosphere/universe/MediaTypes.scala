package com.mesosphere.universe

import akka.http.scaladsl.model.{HttpCharsets, MediaType}

object MediaTypes {

  private[this] def universeRepository(version: String): MediaType = {
    MediaType.applicationWithFixedCharset(
      "vnd.dcos.universe.repo",
      HttpCharsets.`UTF-8`,
      "json")
      .withParams(Map("version" -> version))
  }

  private[this] def universePackage(version: String): MediaType = {
    MediaType.applicationWithFixedCharset(
      "vnd.dcos.universe.package",
      HttpCharsets.`UTF-8`,
      "json")
      .withParams(Map("version" -> version))
  }

  val universeV2Package: MediaType = universePackage("v2")

  val UniverseV3Repository: MediaType = universeRepository("v3")
  val universeV3Package: MediaType = universePackage("v3")

  val UniverseV4Repository: MediaType = universeRepository("v4")
  val universeV4Package: MediaType = universePackage("v4")

  val UniverseV5Repository: MediaType = universeRepository("v5")
  val universeV5Package: MediaType = universePackage("v5")
}
