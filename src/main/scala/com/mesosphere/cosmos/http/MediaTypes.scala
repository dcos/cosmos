package com.mesosphere.cosmos.http

object MediaTypes {
  private[this] def vnd(kind: String): MediaType =
    MediaType(
      "application",
      MediaTypeSubType(s"vnd.dcos.cosmos.$kind", Some("json")),
      Some(Map("charset" -> "utf-8", "version" -> "v1"))
    )

  val applicationJson = MediaType("application", MediaTypeSubType("json"), Some(Map("charset" -> "utf-8")))

  val UninstallRequest = vnd("uninstall-request")
  val UninstallResponse = vnd("uninstall-response")

  val ListRequest = vnd("list-request")
  val ListResponse = vnd("list-response")

  val ErrorResponse = vnd("error")
  val InstallRequest = vnd("install-request")
  val InstallResponse = vnd("install-response")
  val RenderRequest = vnd("render-request")
  val RenderResponse = vnd("render-response")
  val SearchRequest = vnd("search-request")
  val SearchResponse = vnd("search-response")
  val DescribeRequest = vnd("describe-request")
  val DescribeResponse = vnd("describe-response")
  val ListVersionsRequest = vnd("list-versions-request")
  val ListVersionsResponse = vnd("list-versions-response")

}
