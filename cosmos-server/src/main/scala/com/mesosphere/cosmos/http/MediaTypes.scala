package com.mesosphere.cosmos.http

object MediaTypes {
  private[this] def vnd(kind: String): MediaType =
    MediaType(
      "application",
      MediaTypeSubType(s"vnd.dcos.package.$kind", Some("json")),
      Map("charset" -> "utf-8", "version" -> "v1")
    )

  val any = MediaType("*", MediaTypeSubType("*"))
  val applicationJson = MediaType("application", MediaTypeSubType("json"), Map("charset" -> "utf-8"))

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
  val CapabilitiesResponse = MediaType(
    "application",
    MediaTypeSubType("vnd.dcos.capabilities", Some("json")),
    Map("charset" -> "utf-8", "version" -> "v1")
  )

  /* TODO(jsancio): Hack to get the correct vendor type to show. Should separate
   * the vendoer type into a (namespace, type) tuple. Issue #190.
   */
  val PackageRepositoryListRequest = vnd("repository.list-request")
  val PackageRepositoryListResponse = vnd("repository.list-response")
  val PackageRepositoryAddRequest = vnd("repository.add-request")
  val PackageRepositoryAddResponse = vnd("repository.add-response")
  val PackageRepositoryDeleteRequest = vnd("repository.delete-request")
  val PackageRepositoryDeleteResponse = vnd("repository.delete-response")

}
