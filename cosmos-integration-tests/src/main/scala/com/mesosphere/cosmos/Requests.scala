package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.http.CosmosResponse
import com.mesosphere.cosmos.http.HttpRequest
import com.mesosphere.cosmos.http.TestContext
import com.mesosphere.cosmos.rpc.v1.model.ListVersionsResponse
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.Session
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.Deployment
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.util.Await
import io.circe.Decoder
import io.circe.JsonObject
import org.scalatest.Matchers._
import org.scalatest.concurrent.Eventually._
import org.scalatest.time.SpanSugar._

object Requests {

  def installV1(
    name: String,
    version: Option[universe.v2.model.PackageDetailsVersion] = None,
    options: Option[JsonObject] = None,
    appId: Option[AppId] = None
  ): rpc.v1.model.InstallResponse = {
    callEndpoint[rpc.v1.model.InstallResponse](
      CosmosRequests.packageInstallV1(
        rpc.v1.model.InstallRequest(
          name,
          version,
          options,
          appId
        )
      )
    )
  }

  def installV2(
    name: String,
    version: Option[universe.v2.model.PackageDetailsVersion] = None,
    options: Option[JsonObject] = None,
    appId: Option[AppId] = None
  ): rpc.v2.model.InstallResponse = {
    callEndpoint[rpc.v2.model.InstallResponse](
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

  def getHighestReleaseVersion(
    name: String,
    includePackageVersions: Boolean
  ): Option[(universe.v2.model.PackageDetailsVersion, universe.v2.model.ReleaseVersion)] = {
    listPackageVersions(name, includePackageVersions)
      .results.toList.sortBy(_._2.toString.toInt).reverse.headOption
  }

  def listPackageVersions(
    name: String,
    includePackageVersions: Boolean
  ): rpc.v1.model.ListVersionsResponse = {
    callEndpoint[ListVersionsResponse](
      CosmosRequests.packageListVersions(
        rpc.v1.model.ListVersionsRequest(
          name, includePackageVersions
        )
      )
    )
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
  )(
    implicit testContext: TestContext
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

  def getRepository(
    name: String
  ): Option[rpc.v1.model.PackageRepository] = {
    listRepositories().find(_.name == name)
  }

  def getMarathonApp(
    appId: AppId
  ): MarathonApp = {
    Await.result {
      CosmosIntegrationTestClient.adminRouter.getApp(appId)
    }.app
  }

  def isMarathonAppInstalled(appId: AppId): Boolean = {
    Await.result {
      CosmosIntegrationTestClient.adminRouter.getAppOption(appId)
        .map(_.isDefined)
    }
  }

  def waitForDeployments(): Unit = {
    eventually(timeout(5.minutes), interval(5.seconds)) {
      listDeployments() shouldBe empty
    }
    ()
  }

  def listDeployments(): List[Deployment] = {
    Await.result {
      CosmosIntegrationTestClient.adminRouter.listDeployments()
    }
  }

  def callEndpoint[Res: Decoder](request: HttpRequest): Res = {
    submit[Res](request).get
  }

  def submit[Res: Decoder](request: HttpRequest): CosmosResponse[Res] = {
    CosmosResponse[Res](CosmosClient.submit(request))
  }

}
