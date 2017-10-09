package com.mesosphere.cosmos.finch

import com.mesosphere.http.MediaType
import com.mesosphere.http.MediaTypeSubType

object TestingMediaTypes {

  val applicationJson = MediaType("application", MediaTypeSubType("json"), Map("charset" -> "utf-8"))

  val any = MediaType("*", MediaTypeSubType("*"))

}
