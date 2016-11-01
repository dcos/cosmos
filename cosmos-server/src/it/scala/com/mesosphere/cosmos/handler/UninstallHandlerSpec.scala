package com.mesosphere.cosmos.handler

import cats.data.Xor
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.UninstallResponse
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
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
      val installResponse = CosmosClient.doPost(
        path = "package/install",
        requestBody = s"""{"packageName":"cassandra", "appId":"${appId.toString}"}""",
        contentType = Some(MediaTypes.InstallRequest.show),
        accept = Some(MediaTypes.V1InstallResponse.show)
      )
      assertResult(Status.Ok)(installResponse.status)

      val marathonApp = Await.result(adminRouter.getApp(appId))
      assertResult(appId)(marathonApp.app.id)

      //TODO: Assert framework starts up

      val uninstallResponse = CosmosClient.doPost(
        path = "package/uninstall",
        requestBody = """{"packageName":"cassandra"}""",
        contentType = Some(MediaTypes.UninstallRequest.show),
        accept = Some(MediaTypes.UninstallResponse.show)
      )
      val uninstallResponseBody = uninstallResponse.contentString
      assertResult(Status.Ok)(uninstallResponse.status)
      assertResult(MediaTypes.UninstallResponse.show)(uninstallResponse.headerMap("Content-Type"))
      val Xor.Right(body) = decode[UninstallResponse](uninstallResponseBody)
      assert(body.results.flatMap(_.postUninstallNotes).nonEmpty)
    }

    "be able to uninstall multiple packages when 'all' is specified" in {
      // install 'helloworld' twice
      val installBody1 = s"""{"packageName":"helloworld", "appId":"${UUID.randomUUID()}"}"""
      val installResponse1 = CosmosClient.doPost(
        path = "package/install",
        requestBody = installBody1,
        contentType = Some(MediaTypes.InstallRequest.show),
        accept = Some(MediaTypes.V1InstallResponse.show)
      )
      assertResult(Status.Ok, s"install failed: $installBody1")(installResponse1.status)

      val installBody2 = s"""{"packageName":"helloworld", "appId":"${UUID.randomUUID()}"}"""
      val installResponse2 = CosmosClient.doPost(
        path = "package/install",
        requestBody = installBody2,
        contentType = Some(MediaTypes.InstallRequest.show),
        accept = Some(MediaTypes.V1InstallResponse.show)
      )
      assertResult(Status.Ok, s"install failed: $installBody2")(installResponse2.status)

      val uninstallResponse = CosmosClient.doPost(
        path = "package/uninstall",
        requestBody = """{"packageName":"helloworld", "all":true}""",
        contentType = Some(MediaTypes.UninstallRequest.show),
        accept = Some(MediaTypes.UninstallResponse.show)
      )
      assertResult(Status.Ok)(uninstallResponse.status)
      assertResult(MediaTypes.UninstallResponse.show)(uninstallResponse.headerMap("Content-Type"))
    }

    "error when multiple packages are installed and no appId is specified and all isn't set" in {
      // install 'helloworld' twice
      val appId1 = UUID.randomUUID()
      val installBody1 = s"""{"packageName":"helloworld", "appId":"$appId1"}"""
      val installResponse1 = CosmosClient.doPost(
        path = "package/install",
        requestBody = installBody1,
        contentType = Some(MediaTypes.InstallRequest.show),
        accept = Some(MediaTypes.V1InstallResponse.show)
      )
      assertResult(Status.Ok, s"install failed: $installBody1")(installResponse1.status)

      val appId2 = UUID.randomUUID()
      val installBody2 = s"""{"packageName":"helloworld", "appId":"$appId2"}"""
      val installResponse2 = CosmosClient.doPost(
        path = "package/install",
        requestBody = installBody2,
        contentType = Some(MediaTypes.InstallRequest.show),
        accept = Some(MediaTypes.V1InstallResponse.show)
      )
      assertResult(Status.Ok, s"install failed: $installBody2")(installResponse2.status)

      val uninstallResponse = CosmosClient.doPost(
        path = "package/uninstall",
        requestBody = """{"packageName":"helloworld"}""",
        contentType = Some(MediaTypes.UninstallRequest.show),
        accept = Some(MediaTypes.UninstallResponse.show)
      )
      val uninstallResponseBody = uninstallResponse.contentString
      assertResult(Status.BadRequest)(uninstallResponse.status)
      assertResult(MediaTypes.ErrorResponse.show)(uninstallResponse.headerMap("Content-Type"))
      val Xor.Right(err) = decode[ErrorResponse](uninstallResponseBody)
      assertResult(s"Multiple apps named [helloworld] are installed: [/$appId1, /$appId2]")(err.message)

      val cleanupResponse = CosmosClient.doPost(
        path = "package/uninstall",
        requestBody = """{"packageName":"helloworld", "all":true}""",
        contentType = Some(MediaTypes.UninstallRequest.show),
        accept = Some(MediaTypes.UninstallResponse.show)
      )
      assertResult(Status.Ok)(cleanupResponse.status)
    }
  }

}
