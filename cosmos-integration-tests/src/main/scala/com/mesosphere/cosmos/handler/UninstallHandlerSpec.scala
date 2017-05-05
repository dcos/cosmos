package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.InstallRequest
import com.mesosphere.cosmos.rpc.v1.model.UninstallRequest
import com.mesosphere.cosmos.rpc.v1.model.UninstallResponse
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.model.PackageDetails
import com.mesosphere.universe.v2.model.PackageDetailsVersion
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Status
import com.twitter.util.Await
import io.circe.jawn._
import java.util.UUID
import org.scalatest.FreeSpec
import scala.util.Right

final class UninstallHandlerSpec extends FreeSpec {

  import CosmosIntegrationTestClient._
  import UninstallHandlerSpec._

  "The uninstall handler should" - {
    "be able to uninstall a service" in {
      val appId = AppId("cassandra" / "uninstall-test")
      val installRequest = InstallRequest("cassandra", appId = Some(appId))
      val installResponse = submitInstallRequest(installRequest)
      assertResult(Status.Ok)(installResponse.status)

      val marathonApp = Await.result(adminRouter.getApp(appId))
      assertResult(appId)(marathonApp.app.id)

      //TODO: Assert framework starts up

      val uninstallRequest = UninstallRequest("cassandra", appId = None, all = None)
      val uninstallResponse = submitUninstallRequest(uninstallRequest)
      val uninstallResponseBody = uninstallResponse.contentString
      assertResult(Status.Ok)(uninstallResponse.status)
      assertResult(MediaTypes.UninstallResponse.show)(uninstallResponse.headerMap(Fields.ContentType))
      val Right(body) = decode[UninstallResponse](uninstallResponseBody)
      assert(body.results.flatMap(_.postUninstallNotes).nonEmpty)
    }

    "be able to uninstall multiple packages when 'all' is specified" in {
      // install 'helloworld' twice
      val appId1 = AppId(UUID.randomUUID().toString)
      val installRequest1 = InstallRequest("helloworld", appId = Some(appId1))
      val installResponse1 = submitInstallRequest(installRequest1)
      assertResult(Status.Ok, s"install failed: $installRequest1")(installResponse1.status)

      val appId2 = AppId(UUID.randomUUID().toString)
      val installRequest2 = InstallRequest(
        "helloworld",
        packageVersion = Some(PackageDetailsVersion("0.4.1")),
        appId = Some(appId2)
      )
      val installResponse2 = submitInstallRequest(installRequest2)
      assertResult(Status.Ok, s"install failed: $installRequest2")(installResponse2.status)

      val uninstallRequest = UninstallRequest("helloworld", appId = None, all = Some(true))
      val uninstallResponse = submitUninstallRequest(uninstallRequest)
      assertResult(Status.Ok)(uninstallResponse.status)
      assertResult(MediaTypes.UninstallResponse.show)(uninstallResponse.headerMap(Fields.ContentType))
    }

    "error when multiple packages are installed and no appId is specified and all isn't set" in {
      // install 'helloworld' twice
      val appId1 = AppId(UUID.randomUUID().toString)
      val installRequest1 = InstallRequest("helloworld", appId = Some(appId1))
      val installResponse1 = submitInstallRequest(installRequest1)
      assertResult(Status.Ok, s"install failed: $installRequest1")(installResponse1.status)

      val appId2 = AppId(UUID.randomUUID().toString)
      val installRequest2 = InstallRequest("helloworld", appId = Some(appId2))
      val installResponse2 = submitInstallRequest(installRequest2)
      assertResult(Status.Ok, s"install failed: $installRequest2")(installResponse2.status)

      val uninstallRequest = UninstallRequest("helloworld", appId = None, all = None)
      val uninstallResponse = submitUninstallRequest(uninstallRequest)
      val uninstallResponseBody = uninstallResponse.contentString
      assertResult(Status.BadRequest)(uninstallResponse.status)
      assertResult(MediaTypes.ErrorResponse.show)(uninstallResponse.headerMap(Fields.ContentType))
      val Right(err) = decode[ErrorResponse](uninstallResponseBody)
      val expectedMessage = s"Multiple apps named [helloworld] are installed: [$appId1, $appId2]"
      assertResult(expectedMessage)(err.message)

      val cleanupRequest = UninstallRequest("helloworld", appId = None, all = Some(true))
      val cleanupResponse = submitUninstallRequest(cleanupRequest)
      assertResult(Status.Ok)(cleanupResponse.status)
    }
  }

}

object UninstallHandlerSpec {

  def submitInstallRequest(installRequest: InstallRequest): Response = {
    CosmosClient.submit(CosmosRequests.packageInstallV1(installRequest))
  }

  def submitUninstallRequest(uninstallRequest: UninstallRequest): Response = {
    CosmosClient.submit(CosmosRequests.packageUninstall(uninstallRequest))
  }

}
