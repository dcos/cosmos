package com.mesosphere.universe

import com.mesosphere.http.MediaType
import com.mesosphere.http.MediaTypeSubType

object MediaTypes {

  private[this] def universeRepository(version: String): MediaType = {
    MediaType(
      "application",
      MediaTypeSubType("vnd.dcos.universe.repo", Some("json")),
      Map("charset" -> "utf-8", "version" -> version)
    )
  }

  private[this] def universePackage(version: String): MediaType = {
    MediaType(
      "application",
      MediaTypeSubType("vnd.dcos.universe.package", Some("json")),
      Map("charset" -> "utf-8", "version" -> version)
    )
  }

  val applicationZip = MediaType("application", MediaTypeSubType("zip"))
  val UniverseV2Repository: MediaType = applicationZip

  val UniverseV3Repository: MediaType = universeRepository("v3")
  val UniverseV4Repository: MediaType = universeRepository("v4")

  val universeV4Package: MediaType = universePackage("v4")

  val universeV3Package: MediaType = universePackage("v3")

  val universeV2Package: MediaType = universePackage("v2")

  val PackageZip: MediaType = MediaType(
    "application",
    MediaTypeSubType("vnd.dcos.universe.package", Some("zip")),
    Map("version" -> "v1")
  )

}
