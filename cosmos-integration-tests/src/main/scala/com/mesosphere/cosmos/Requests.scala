package com.mesosphere.cosmos

import com.mesosphere.cosmos.error.ServiceUnavailable
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.http.HttpRequest
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import com.netaporter.uri.Uri
import io.circe.Decoder
import io.circe.JsonObject

object Requests {

  def install(
    name: String,
    version: Option[universe.v2.model.PackageDetailsVersion] = None,
    options: Option[JsonObject] = None,
    appId: Option[AppId] = None
  ): rpc.v1.model.InstallResponse = {
    callEndpoint[rpc.v1.model.InstallResponse](
      CosmosRequests.packageInstallV2(
        rpc.v1.model.InstallRequest(
          name,
          version,
          options,
          appId
        )
      )
    )
  }

  def callEndpoint[Res](request: HttpRequest)(implicit
    decoder: Decoder[Res]
  ): Res = {
    val (status, response) = CosmosClient.call[Res](request)
    response match {
      case Left(errorResponse) if errorResponse.`type` == "ServiceUnavailable" =>
        throw ServiceUnavailable(errorResponse.data.toString).exception
      case Left(errorResponse) =>
        throw HttpErrorResponse(status, errorResponse)
      case Right(res) =>
        res
    }
  }

  def uninstall(
    name: String,
    appId: Option[AppId] = None,
    all: Option[Boolean] = None
  ): rpc.v1.model.UninstallResponse = {
    callEndpoint[rpc.v1.model.UninstallResponse](
      CosmosRequests.packageUninstall(
        rpc.v1.model.UninstallRequest(
          packageName = name,
          appId = appId,
          all = all
        )
      )
    )
  }

  def listRepositories(
  ): List[rpc.v1.model.PackageRepository] = {
    callEndpoint[rpc.v1.model.PackageRepositoryListResponse](
      CosmosRequests.packageRepositoryList
    ).repositories.toList
  }

  def listPackages(
    name: Option[String] = None,
    appId: Option[AppId] = None
  ): List[rpc.v1.model.Installation] = {
    callEndpoint[rpc.v1.model.ListResponse](
      CosmosRequests.packageList(
        rpc.v1.model.ListRequest(
          name,
          appId
        )
      )
    ).packages.toList
  }

  def deleteRepository(
    name: Option[String] = None,
    uri: Option[Uri] = None
  ): rpc.v1.model.PackageRepositoryDeleteResponse = {
    callEndpoint[rpc.v1.model.PackageRepositoryDeleteResponse](
      CosmosRequests.packageRepositoryDelete(
        rpc.v1.model.PackageRepositoryDeleteRequest(
          name,
          uri
        )
      )
    )
  }

  def addRepository(
    name: String,
    uri: Uri,
    index: Option[Int] = None
  ): rpc.v1.model.PackageRepositoryAddResponse = {
    callEndpoint[rpc.v1.model.PackageRepositoryAddResponse](
      CosmosRequests.packageRepositoryAdd(
        rpc.v1.model.PackageRepositoryAddRequest(
          name,
          uri,
          index
        )
      )
    )
  }

  def describeService(
    appId: AppId
  ): rpc.v1.model.ServiceDescribeResponse = {
    callEndpoint[rpc.v1.model.ServiceDescribeResponse](
      CosmosRequests.serviceDescribe(
        rpc.v1.model.ServiceDescribeRequest(
          appId
        )
      )
    )
  }

  def describePackage(
    name: String,
    version: Option[universe.v2.model.PackageDetailsVersion]
  ): rpc.v3.model.DescribeResponse = {
    callEndpoint[rpc.v3.model.DescribeResponse](
      CosmosRequests.packageDescribeV3(
        rpc.v1.model.DescribeRequest(
          name,
          version
        )
      )
    )
  }

}
