package com.mesosphere.cosmos

import _root_.io.circe.JsonObject
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.repository.DefaultRepositories
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Status
import com.twitter.util.Await
import scala.concurrent.duration._

object ItUtil {

  def listRepositories(): Seq[rpc.v1.model.PackageRepository] = {
    val request = CosmosRequests.packageRepositoryList
    val Right(response) = CosmosClient.callEndpoint[rpc.v1.model.PackageRepositoryListResponse](
      request
    )
    response.repositories
  }

  def deleteRepositoryEither(
    name: Option[String] = None,
    uri: Option[Uri] = None
  ): (Status, Either[rpc.v1.model.ErrorResponse, rpc.v1.model.PackageRepositoryDeleteResponse]) = {
    val repoDeleteRequest = rpc.v1.model.PackageRepositoryDeleteRequest(name, uri)
    val request = CosmosRequests.packageRepositoryDelete(repoDeleteRequest)
    CosmosClient.call[rpc.v1.model.PackageRepositoryDeleteResponse](
      request
    )
  }

  def deleteRepository(
    name: Option[String] = None,
    uri: Option[Uri] = None
  ): rpc.v1.model.PackageRepositoryDeleteResponse = {
    val repoDeleteRequest = rpc.v1.model.PackageRepositoryDeleteRequest(name, uri)
    val request = CosmosRequests.packageRepositoryDelete(repoDeleteRequest)
    val Right(response) = CosmosClient.callEndpoint[rpc.v1.model.PackageRepositoryDeleteResponse](
      request
    )
    response
  }

  def addRepositoryEither(
    source: rpc.v1.model.PackageRepository,
    index: Option[Int] = None
  ): (Status, Either[rpc.v1.model.ErrorResponse, rpc.v1.model.PackageRepositoryAddResponse]) = {
    val repoAddRequest = rpc.v1.model.PackageRepositoryAddRequest(source.name, source.uri, index)
    val request = CosmosRequests.packageRepositoryAdd(repoAddRequest)
    CosmosClient.call[rpc.v1.model.PackageRepositoryAddResponse](
      request
    )
  }

  def addRepository(
    source: rpc.v1.model.PackageRepository
  ): rpc.v1.model.PackageRepositoryAddResponse = {
    val repoAddRequest = rpc.v1.model.PackageRepositoryAddRequest(source.name, source.uri)
    val request = CosmosRequests.packageRepositoryAdd(repoAddRequest)
    val Right(response) = CosmosClient.callEndpoint[rpc.v1.model.PackageRepositoryAddResponse](
      request
    )
    response
  }

  def replaceRepositoriesWith(repositories: Seq[rpc.v1.model.PackageRepository]): Unit = {
    listRepositories().foreach { repo =>
      deleteRepository(Some(repo.name))
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
    version: Option[String],
    options: Option[JsonObject] = None,
    appId: Option[AppId] = None
  ): Either[rpc.v1.model.ErrorResponse, rpc.v1.model.InstallResponse] = {
    val detailsVersion = version.map(universe.v2.model.PackageDetailsVersion)
    CosmosClient.callEndpoint[rpc.v1.model.InstallResponse](
      CosmosRequests.packageInstallV2(
        rpc.v1.model.InstallRequest(name, detailsVersion, options, appId)
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

  def waitForDeployment(adminRouter: AdminRouter)(attempts: Int): Boolean = {
    Stream.tabulate(attempts) { _ =>
      Thread.sleep(1.second.toMillis)
      val deployments = Await.result {
        adminRouter.listDeployments()(CosmosIntegrationTestClient.Session)
      }

      deployments.isEmpty
    }.dropWhile(done => !done).nonEmpty
  }


  def insert[T](seq: Seq[T], index: Int, value: T): Seq[T] = {
    val (front, back) = seq.splitAt(index)
    front ++ Seq(value) ++ back
  }

}
