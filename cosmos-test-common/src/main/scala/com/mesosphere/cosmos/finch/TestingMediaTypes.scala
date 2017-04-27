package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypeSubType

object TestingMediaTypes {

  val applicationJson = MediaType("application", MediaTypeSubType("json"), Map("charset" -> "utf-8"))

  val any = MediaType("*", MediaTypeSubType("*"))

}
