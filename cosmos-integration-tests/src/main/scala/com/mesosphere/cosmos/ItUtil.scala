package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient

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

}
