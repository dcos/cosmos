package com.mesosphere.universe

import com.mesosphere.cosmos.http.{MediaType, MediaTypeSubType}

object MediaTypes {

  val applicationZip = MediaType("application", MediaTypeSubType("zip"))
  val UniverseV2Repository = applicationZip

  val UniverseV3Repository = MediaType(
    "application",
    MediaTypeSubType("vnd.dcos.universe.repo", Some("json")),
    Map("charset" -> "utf-8", "version" -> "v3")
  )

}
