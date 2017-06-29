package com.mesosphere.cosmos.rpc

import com.mesosphere.cosmos.http.MediaType

object MediaTypes {

  val dcos = MediaType.vndJson(List("dcos")) _
  val dcosPackage = MediaType.vndJson(List("dcos", "package")) _
  val dcosService = MediaType.vndJson(List("dcos", "service")) _

  val UninstallRequest = dcosPackage("uninstall-request", 1)
  val UninstallResponse = dcosPackage("uninstall-response", 1)

  val ListRequest = dcosPackage("list-request", 1)
  val ListResponse = dcosPackage("list-response", 1)

  val ErrorResponse = dcosPackage("error", 1)
  val InstallRequest = dcosPackage("install-request", 1)
  val RenderRequest = dcosPackage("render-request", 1)
  val RenderResponse = dcosPackage("render-response", 1)
  val SearchRequest = dcosPackage("search-request", 1)
  val SearchResponse = dcosPackage("search-response", 1)
  val DescribeRequest = dcosPackage("describe-request", 1)
  val ListVersionsRequest = dcosPackage("list-versions-request", 1)
  val ListVersionsResponse = dcosPackage("list-versions-response", 1)
  val CapabilitiesResponse = dcos("capabilities", 1)

  val PackageRepositoryListRequest = dcosPackage("repository.list-request", 1)
  val PackageRepositoryListResponse = dcosPackage("repository.list-response", 1)
  val PackageRepositoryAddRequest = dcosPackage("repository.add-request", 1)
  val PackageRepositoryAddResponse = dcosPackage("repository.add-response", 1)
  val PackageRepositoryDeleteRequest = dcosPackage("repository.delete-request", 1)
  val PackageRepositoryDeleteResponse = dcosPackage("repository.delete-response", 1)

  val V2DescribeResponse = dcosPackage("describe-response", 2)
  val V3DescribeResponse = dcosPackage("describe-response", 3)
  val V1InstallResponse = dcosPackage("install-response", 1)
  val V2InstallResponse = dcosPackage("install-response", 2)
  val V1ListResponse = dcosPackage("list-response", 1)
  val V2ListResponse = dcosPackage("list-response", 2)

  val ServiceDescribeRequest = dcosService("describe-request", 1)
  val ServiceDescribeResponse = dcosService("describe-response", 1)
}
