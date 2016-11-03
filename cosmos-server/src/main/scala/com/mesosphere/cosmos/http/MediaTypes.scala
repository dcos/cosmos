package com.mesosphere.cosmos.http

object MediaTypes {

  // RPC Media Types
  val applicationJson = MediaType("application", MediaTypeSubType("json"), Map("charset" -> "utf-8"))
  val applicationOctetStream = MediaType("application", MediaTypeSubType("octet-stream"))

  // Storage Media Types
  val RepositoryList = MediaType.vndJson(List("dcos", "package"))("repository.repo-list", 1)
  val OperationStatus = MediaType.vndJson(List("dcos", "package"))("operation.status", 1)

}
