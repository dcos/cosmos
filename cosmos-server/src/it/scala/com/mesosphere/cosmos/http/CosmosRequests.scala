package com.mesosphere.cosmos.http

import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.PackageRepositoryListRequest
import com.mesosphere.cosmos.rpc.v1.model.RenderRequest
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.io.Buf

object CosmosRequests {

  val capabilities: HttpRequest = {
    HttpRequest.get(path = "capabilities", accept = rpc.MediaTypes.CapabilitiesResponse)
  }

  def packageAdd(packageData: Buf): HttpRequest = {
    HttpRequest.post(
      path = "package/add",
      body = packageData,
      contentType = universe.MediaTypes.PackageZip,
      accept = rpc.MediaTypes.AddResponse
    )
  }

  def packageAdd(requestBody: rpc.v1.model.UniverseAddRequest): HttpRequest = {
    HttpRequest.post(
      path = "package/add",
      body = requestBody,
      contentType = rpc.MediaTypes.AddRequest,
      accept = rpc.MediaTypes.AddResponse
    )
  }

  def packageDescribeV1(describeRequest: rpc.v1.model.DescribeRequest): HttpRequest = {
    packageDescribe(describeRequest, accept = rpc.MediaTypes.V1DescribeResponse)
  }

  def packageDescribeV2(
    packageName: String,
    packageVersion: Option[universe.v3.model.PackageDefinition.Version]
  ): HttpRequest = {
    val oldVersion = packageVersion.as[Option[universe.v2.model.PackageDetailsVersion]]
    val describeRequest = rpc.v1.model.DescribeRequest(packageName, oldVersion)
    packageDescribeV2(describeRequest)
  }

  def packageDescribeV2(describeRequest: rpc.v1.model.DescribeRequest): HttpRequest = {
    packageDescribe(describeRequest, accept = rpc.MediaTypes.V2DescribeResponse)
  }

  def packageInstallV1(installRequest: rpc.v1.model.InstallRequest): HttpRequest = {
    packageInstall(installRequest, accept = rpc.MediaTypes.V1InstallResponse)
  }

  def packageInstallV2(installRequest: rpc.v1.model.InstallRequest): HttpRequest = {
    packageInstall(installRequest, accept = rpc.MediaTypes.V2InstallResponse)
  }

  def packageList(listRequest: rpc.v1.model.ListRequest): HttpRequest = {
    HttpRequest.post(
      "package/list",
      listRequest,
      rpc.MediaTypes.ListRequest,
      rpc.MediaTypes.ListResponse
    )
  }

  def packageListVersions(listVersionsRequest: rpc.v1.model.ListVersionsRequest): HttpRequest = {
    HttpRequest.post(
      path = "package/list-versions",
      body = listVersionsRequest,
      contentType = rpc.MediaTypes.ListVersionsRequest,
      accept = rpc.MediaTypes.ListVersionsResponse
    )
  }

  def packageRender(renderRequest: RenderRequest): HttpRequest = {
    HttpRequest.post(
      path = "package/render",
      body = renderRequest,
      contentType = rpc.MediaTypes.RenderRequest,
      accept = rpc.MediaTypes.RenderResponse
    )
  }

  def packageSearch(searchRequest: rpc.v1.model.SearchRequest): HttpRequest = {
    HttpRequest.post(
      path = "package/search",
      body = searchRequest,
      contentType = rpc.MediaTypes.SearchRequest,
      accept = rpc.MediaTypes.SearchResponse
    )
  }

  def packageRepositoryAdd(
    repositoryAddRequest: rpc.v1.model.PackageRepositoryAddRequest
  ): HttpRequest = {
    HttpRequest.post(
      path = "package/repository/add",
      body = repositoryAddRequest,
      contentType = rpc.MediaTypes.PackageRepositoryAddRequest,
      accept = rpc.MediaTypes.PackageRepositoryAddResponse
    )
  }

  def packageRepositoryDelete(
    repositoryDeleteRequest: rpc.v1.model.PackageRepositoryDeleteRequest
  ): HttpRequest = {
    HttpRequest.post(
      path = "package/repository/delete",
      body = repositoryDeleteRequest,
      contentType = rpc.MediaTypes.PackageRepositoryDeleteRequest,
      accept = rpc.MediaTypes.PackageRepositoryDeleteResponse
    )
  }

  val packageRepositoryList: HttpRequest = {
    HttpRequest.post(
      path = "package/repository/list",
      body = PackageRepositoryListRequest(),
      contentType = rpc.MediaTypes.PackageRepositoryListRequest,
      accept = rpc.MediaTypes.PackageRepositoryListResponse
    )
  }

  def packageUninstall(uninstallRequest: rpc.v1.model.UninstallRequest): HttpRequest = {
    HttpRequest.post(
      path = "package/uninstall",
      body = uninstallRequest,
      contentType = rpc.MediaTypes.UninstallRequest,
      accept = rpc.MediaTypes.UninstallResponse
    )
  }

  private def packageDescribe(
    describeRequest: rpc.v1.model.DescribeRequest,
    accept: MediaType
  ): HttpRequest = {
    HttpRequest.post(
      path = "package/describe",
      body = describeRequest,
      contentType = rpc.MediaTypes.DescribeRequest,
      accept = accept
    )
  }

  private def packageInstall(
    installRequest: rpc.v1.model.InstallRequest,
    accept: MediaType
  ): HttpRequest = {
    HttpRequest.post(
      path = "package/install",
      body = installRequest,
      contentType = rpc.MediaTypes.InstallRequest,
      accept = accept
    )
  }

}
