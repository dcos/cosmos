package com.mesosphere.cosmos.http

import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.PackageRepositoryListRequest
import com.mesosphere.cosmos.rpc.v1.model.RenderRequest

object CosmosRequests {

  val capabilities: HttpRequest = {
    HttpRequest.get(
      path = RawRpcPath("/capabilities"),
      accept = rpc.MediaTypes.CapabilitiesResponse
    )
  }

  def packageDescribeV2(
    describeRequest: rpc.v1.model.DescribeRequest
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    packageDescribe(describeRequest, accept = rpc.MediaTypes.V2DescribeResponse)
  }

  def packageDescribeV3(
    describeRequest: rpc.v1.model.DescribeRequest
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    packageDescribe(describeRequest, accept = rpc.MediaTypes.V3DescribeResponse)
  }

  def packageInstallV1(
    installRequest: rpc.v1.model.InstallRequest
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    packageInstall(installRequest, accept = rpc.MediaTypes.V1InstallResponse)
  }

  def packageInstallV2(
    installRequest: rpc.v1.model.InstallRequest
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    packageInstall(installRequest, accept = rpc.MediaTypes.V2InstallResponse)
  }

  def packageList(
    listRequest: rpc.v1.model.ListRequest
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    HttpRequest.post(
      PackageRpcPath("list"),
      listRequest,
      rpc.MediaTypes.ListRequest,
      rpc.MediaTypes.ListResponse
    )
  }

  def packageListVersions(
    listVersionsRequest: rpc.v1.model.ListVersionsRequest
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    HttpRequest.post(
      path = PackageRpcPath("list-versions"),
      body = listVersionsRequest,
      contentType = rpc.MediaTypes.ListVersionsRequest,
      accept = rpc.MediaTypes.ListVersionsResponse
    )
  }

  def packageRender(
    renderRequest: RenderRequest
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    HttpRequest.post(
      path = PackageRpcPath("render"),
      body = renderRequest,
      contentType = rpc.MediaTypes.RenderRequest,
      accept = rpc.MediaTypes.RenderResponse
    )
  }

  def packageSearch(
    searchRequest: rpc.v1.model.SearchRequest
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    HttpRequest.post(
      path = PackageRpcPath("search"),
      body = searchRequest,
      contentType = rpc.MediaTypes.SearchRequest,
      accept = rpc.MediaTypes.SearchResponse
    )
  }

  def packageRepositoryAdd(
    repositoryAddRequest: rpc.v1.model.PackageRepositoryAddRequest
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    HttpRequest.post(
      path = PackageRpcPath("repository/add"),
      body = repositoryAddRequest,
      contentType = rpc.MediaTypes.PackageRepositoryAddRequest,
      accept = rpc.MediaTypes.PackageRepositoryAddResponse
    )
  }

  def packageRepositoryDelete(
    repositoryDeleteRequest: rpc.v1.model.PackageRepositoryDeleteRequest
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    HttpRequest.post(
      path = PackageRpcPath("repository/delete"),
      body = repositoryDeleteRequest,
      contentType = rpc.MediaTypes.PackageRepositoryDeleteRequest,
      accept = rpc.MediaTypes.PackageRepositoryDeleteResponse
    )
  }

  def packageRepositoryList(implicit testContext: TestContext): HttpRequest = {
    HttpRequest.post(
      path = PackageRpcPath("repository/list"),
      body = PackageRepositoryListRequest(),
      contentType = rpc.MediaTypes.PackageRepositoryListRequest,
      accept = rpc.MediaTypes.PackageRepositoryListResponse
    )
  }

  def packageUninstall(
    uninstallRequest: rpc.v1.model.UninstallRequest
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    HttpRequest.post(
      path = PackageRpcPath("uninstall"),
      body = uninstallRequest,
      contentType = rpc.MediaTypes.UninstallRequest,
      accept = rpc.MediaTypes.UninstallResponse
    )
  }

  def serviceDescribe(
    describeRequest: rpc.v1.model.ServiceDescribeRequest
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    HttpRequest.post(
      path = ServiceRpcPath("describe"),
      body = describeRequest,
      contentType =  rpc.MediaTypes.ServiceDescribeRequest,
      accept = rpc.MediaTypes.ServiceDescribeResponse
    )
  }

  private def packageDescribe(
    describeRequest: rpc.v1.model.DescribeRequest,
    accept: MediaType
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    HttpRequest.post(
      path = PackageRpcPath("describe"),
      body = describeRequest,
      contentType = rpc.MediaTypes.DescribeRequest,
      accept = accept
    )
  }

  private def packageInstall(
    installRequest: rpc.v1.model.InstallRequest,
    accept: MediaType
  )(
    implicit testContext: TestContext
  ): HttpRequest = {
    HttpRequest.post(
      path = PackageRpcPath("install"),
      body = installRequest,
      contentType = rpc.MediaTypes.InstallRequest,
      accept = accept
    )
  }

}
