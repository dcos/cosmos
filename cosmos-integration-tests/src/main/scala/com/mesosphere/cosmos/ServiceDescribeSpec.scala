package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.circe.jawn
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.universe
import com.twitter.finagle.http.Status
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

import _root_.io.circe.syntax._

class ServiceDescribeSpec
  extends FreeSpec
    with Matchers
    with TableDrivenPropertyChecks {

  "The service/describe endpoint" - {
    "should be able to describe a running service" in {
      forAll(packages) { (packageName, packageVersion, description) =>
        val Right(install) = CosmosClient.callEndpoint[rpc.v1.model.InstallResponse](
          CosmosRequests.packageInstallV2(
            rpc.v1.model.InstallRequest(
              packageName,
              Some(universe.v2.model.PackageDetailsVersion(packageVersion)))
          )
        )

        val appId = install.appId

        val describe = CosmosClient.submit(
          CosmosRequests.serviceDescribe(
            rpc.v1.model.ServiceDescribeRequest(
              appId = appId
            )
          )
        )

        describe.status shouldBe Status.Ok
        describe.contentType shouldBe rpc.MediaTypes.ServiceDescribeResponse
        jawn.parse(describe.contentString) shouldBe description

        val uninstall = CosmosClient.callEndpoint[rpc.v1.model.UninstallResponse](
          CosmosRequests.packageUninstall(
            rpc.v1.model.UninstallRequest(
              packageName = packageName,
              appId = Some(appId),
              all = None
            )
          )
        )

      }
    }
  }

  private def helloWorldPackage = {

  }

  private val packages = {
    Table(
      ("Package Name", "Package Version", "Description"),
      ("helloworld", "0.1.0", ???),
      ("hellworld", "0.4.0", ???),
      ("helloworld", "0.4.1", ???)
    )
  }

  private def helloworldDescription: Json = {
    Map(
      "upgradesTo" -> List().asJson,
      "downgradesTo" -> List().asJson,
      "package" -> helloWorldPackage,
      "resolvedOptions" -> ???
    ).asJson
  }

}

