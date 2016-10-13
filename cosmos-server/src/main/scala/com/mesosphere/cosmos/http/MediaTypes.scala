package com.mesosphere.cosmos.http

object MediaTypes {

  // RPC Media Types
  val applicationJson = MediaType("application", MediaTypeSubType("json"), Map("charset" -> "utf-8"))

  // Storage Media Types
  val RepositoryList = MediaType.vndJson(List("dcos", "package"))("repository.repo-list", 1)

}
