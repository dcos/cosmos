package com.mesosphere.cosmos.handler

import cats.data.Xor
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.UninstallResponse
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.test.CosmosRequest
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.Status
import com.twitter.util.Await
import io.circe.jawn._
import java.util.UUID
import org.scalatest.FreeSpec

final class UninstallHandlerSpec extends FreeSpec {

  import CosmosIntegrationTestClient._

  "The uninstall handler should" - {
    "be able to uninstall a service" in {
      val appId = AppId("cassandra" / "uninstall-test")
      val installRequest = CosmosRequest.post(
        "package/install",
        s"""{"packageName":"cassandra", "appId":"${appId.toString}"}""",
        Some(MediaTypes.InstallRequest.show),
        Some(MediaTypes.V1InstallResponse.show)
      )
      val installResponse = CosmosClient.submit(installRequest)
      assertResult(Status.Ok)(installResponse.status)

      val marathonApp = Await.result(adminRouter.getApp(appId))
      assertResult(appId)(marathonApp.app.id)

      //TODO: Assert framework starts up

      val uninstallRequest = CosmosRequest.post(
        "package/uninstall",
        """{"packageName":"cassandra"}""",
        Some(MediaTypes.UninstallRequest.show),
        Some(MediaTypes.UninstallResponse.show)
      )
      val uninstallResponse = CosmosClient.submit(uninstallRequest)
      val uninstallResponseBody = uninstallResponse.contentString
      assertResult(Status.Ok)(uninstallResponse.status)
      assertResult(MediaTypes.UninstallResponse.show)(uninstallResponse.headerMap("Content-Type"))
      val Xor.Right(body) = decode[UninstallResponse](uninstallResponseBody)
      assert(body.results.flatMap(_.postUninstallNotes).nonEmpty)
    }

    "be able to uninstall multiple packages when 'all' is specified" in {
      // install 'helloworld' twice
      val installBody1 = s"""{"packageName":"helloworld", "appId":"${UUID.randomUUID()}"}"""
      val installRequest1 = CosmosRequest.post(
        "package/install",
        installBody1,
        Some(MediaTypes.InstallRequest.show),
        Some(MediaTypes.V1InstallResponse.show)
      )
      val installResponse1 = CosmosClient.submit(installRequest1)
      assertResult(Status.Ok, s"install failed: $installBody1")(installResponse1.status)

      val installBody2 = s"""{"packageName":"helloworld", "appId":"${UUID.randomUUID()}"}"""
      val installRequest2 = CosmosRequest.post(
        "package/install",
        installBody2,
        Some(MediaTypes.InstallRequest.show),
        Some(MediaTypes.V1InstallResponse.show)
      )
      val installResponse2 = CosmosClient.submit(installRequest2)
      assertResult(Status.Ok, s"install failed: $installBody2")(installResponse2.status)

      val uninstallRequest = CosmosRequest.post(
        "package/uninstall",
        """{"packageName":"helloworld", "all":true}""",
        Some(MediaTypes.UninstallRequest.show),
        Some(MediaTypes.UninstallResponse.show)
      )
      val uninstallResponse = CosmosClient.submit(uninstallRequest)
      assertResult(Status.Ok)(uninstallResponse.status)
      assertResult(MediaTypes.UninstallResponse.show)(uninstallResponse.headerMap("Content-Type"))
    }

    "error when multiple packages are installed and no appId is specified and all isn't set" in {
      // install 'helloworld' twice
      val appId1 = UUID.randomUUID()
      val installBody1 = s"""{"packageName":"helloworld", "appId":"$appId1"}"""
      val installRequest1 = CosmosRequest.post(
        "package/install",
        installBody1,
        Some(MediaTypes.InstallRequest.show),
        Some(MediaTypes.V1InstallResponse.show)
      )
      val installResponse1 = CosmosClient.submit(installRequest1)
      assertResult(Status.Ok, s"install failed: $installBody1")(installResponse1.status)

      val appId2 = UUID.randomUUID()
      val installBody2 = s"""{"packageName":"helloworld", "appId":"$appId2"}"""
      val installRequest2 = CosmosRequest.post(
        "package/install",
        installBody2,
        Some(MediaTypes.InstallRequest.show),
        Some(MediaTypes.V1InstallResponse.show)
      )
      val installResponse2 = CosmosClient.submit(installRequest2)
      assertResult(Status.Ok, s"install failed: $installBody2")(installResponse2.status)

      val uninstallRequest = CosmosRequest.post(
        "package/uninstall",
        """{"packageName":"helloworld"}""",
        Some(MediaTypes.UninstallRequest.show),
        Some(MediaTypes.UninstallResponse.show)
      )
      val uninstallResponse = CosmosClient.submit(uninstallRequest)
      val uninstallResponseBody = uninstallResponse.contentString
      assertResult(Status.BadRequest)(uninstallResponse.status)
      assertResult(MediaTypes.ErrorResponse.show)(uninstallResponse.headerMap("Content-Type"))
      val Xor.Right(err) = decode[ErrorResponse](uninstallResponseBody)
      assertResult(s"Multiple apps named [helloworld] are installed: [/$appId1, /$appId2]")(err.message)

      val cleanupRequest = CosmosRequest.post(
        "package/uninstall",
        """{"packageName":"helloworld", "all":true}""",
        Some(MediaTypes.UninstallRequest.show),
        Some(MediaTypes.UninstallResponse.show)
      )
      val cleanupResponse = CosmosClient.submit(cleanupRequest)
      assertResult(Status.Ok)(cleanupResponse.status)
    }
  }

}
