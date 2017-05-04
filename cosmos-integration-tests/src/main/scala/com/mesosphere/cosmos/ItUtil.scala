package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.repository.DefaultRepositories
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe

object ItUtil {

  def listRepositories(): Seq[rpc.v1.model.PackageRepository] = {
    val request = CosmosRequests.packageRepositoryList
    val Right(response) = CosmosClient.callEndpoint[rpc.v1.model.PackageRepositoryListResponse](request)
    response.repositories
  }

  def deleteRepository(
    source: rpc.v1.model.PackageRepository
  ): rpc.v1.model.PackageRepositoryDeleteResponse = {
    val repoDeleteRequest = rpc.v1.model.PackageRepositoryDeleteRequest(name = Some(source.name))
    val request = CosmosRequests.packageRepositoryDelete(repoDeleteRequest)
    val Right(response) = CosmosClient.callEndpoint[rpc.v1.model.PackageRepositoryDeleteResponse](request)
    response
  }

  def addRepository(
    source: rpc.v1.model.PackageRepository
  ): rpc.v1.model.PackageRepositoryAddResponse = {
    val repoAddRequest = rpc.v1.model.PackageRepositoryAddRequest(source.name, source.uri)
    val request = CosmosRequests.packageRepositoryAdd(repoAddRequest)
    val Right(response) = CosmosClient.callEndpoint[rpc.v1.model.PackageRepositoryAddResponse](request)
    response
  }

  def replaceRepositoriesWith(repositories: Seq[rpc.v1.model.PackageRepository]): Unit = {
    listRepositories().foreach { repo =>
      deleteRepository(repo)
    }
    repositories.foreach { repo =>
      addRepository(repo)
    }
  }

  def getRepoByName(name: String): String = {
    DefaultRepositories()
      .getOrThrow
      .find(_.name == name)
      .map(_.uri.toString)
      .get
  }

  def packageInstall(
    name: String,
    version: Option[String]
  ): Either[rpc.v1.model.ErrorResponse, rpc.v1.model.InstallResponse] = {
    val detailsVersion = version.map(universe.v2.model.PackageDetailsVersion)
    CosmosClient.callEndpoint[rpc.v1.model.InstallResponse](
      CosmosRequests.packageInstallV2(
        rpc.v1.model.InstallRequest(name, detailsVersion)
      )
    )
  }

  def packageUninstall(
    name: String,
    appId: AppId,
    all: Boolean
  ): Either[rpc.v1.model.ErrorResponse, rpc.v1.model.UninstallResponse] = {
    CosmosClient.callEndpoint[rpc.v1.model.UninstallResponse](
      CosmosRequests.packageUninstall(
        rpc.v1.model.UninstallRequest(
          packageName = name,
          appId = Some(appId),
          all = Some(all)
        )
      )
    )
  }

}
