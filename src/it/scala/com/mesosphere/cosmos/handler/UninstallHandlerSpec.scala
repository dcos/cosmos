package com.mesosphere.cosmos.handler

import java.util.UUID

import cats.data.Xor
import com.mesosphere.cosmos.ErrorResponse
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model.{AppId, UninstallResponse}
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient._
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.Status
import com.twitter.io.Buf
import com.twitter.util.Await
import io.circe.parse._
import org.scalatest.FreeSpec

final class UninstallHandlerSpec extends FreeSpec {

  "The uninstall handler should" - {
    "be able to uninstall a service" in {
      val installRequest = CosmosClient.requestBuilder("package/install")
        .addHeader("Content-Type", MediaTypes.InstallRequest.show)
        .addHeader("Accept", MediaTypes.InstallResponse.show)
        .buildPost(Buf.Utf8("""{"packageName":"cassandra","options":{}}"""))
      val installResponse = CosmosClient(installRequest)
      assertResult(Status.Ok)(installResponse.status)

      val appId = AppId("cassandra" / "dcos")
      val marathonApp = Await.result(adminRouter.getApp(appId))
      assertResult(appId)(marathonApp.app.id)

      //TODO: Assert framework starts up

      val uninstallRequest = CosmosClient.requestBuilder("package/uninstall")
        .setHeader("Accept", MediaTypes.UninstallResponse.show)
        .setHeader("Content-Type", MediaTypes.UninstallRequest.show)
        .buildPost(Buf.Utf8("""{"packageName":"cassandra"}"""))
      val uninstallResponse = CosmosClient(uninstallRequest)
      val uninstallResponseBody = uninstallResponse.contentString
      assertResult(Status.Ok)(uninstallResponse.status)
      assertResult(MediaTypes.UninstallResponse.show)(uninstallResponse.headerMap("Content-Type"))
      val Xor.Right(body) = decode[UninstallResponse](uninstallResponseBody)
      assert(body.results.flatMap(_.postUninstallNotes).nonEmpty)
    }

    "be able to uninstall multiple packages when 'all' is specified" in {
      // install 'helloworld' twice
      val installBody1 = s"""{"packageName":"helloworld", "appId":"${UUID.randomUUID()}"}"""
      val installRequest1 = CosmosClient.requestBuilder("package/install")
        .addHeader("Content-Type", MediaTypes.InstallRequest.show)
        .addHeader("Accept", MediaTypes.InstallResponse.show)
        .buildPost(Buf.Utf8(installBody1))
      val installResponse1 = CosmosClient(installRequest1)
      assertResult(Status.Ok, s"install failed: $installBody1")(installResponse1.status)

      val installBody2 = s"""{"packageName":"helloworld", "appId":"${UUID.randomUUID()}"}"""
      val installRequest2 = CosmosClient.requestBuilder("package/install")
        .addHeader("Content-Type", MediaTypes.InstallRequest.show)
        .addHeader("Accept", MediaTypes.InstallResponse.show)
        .buildPost(Buf.Utf8(installBody2))
      val installResponse2 = CosmosClient(installRequest2)
      assertResult(Status.Ok, s"install failed: $installBody2")(installResponse2.status)

      val uninstallRequest = CosmosClient.requestBuilder("package/uninstall")
        .setHeader("Accept", MediaTypes.UninstallResponse.show)
        .setHeader("Content-Type", MediaTypes.UninstallRequest.show)
        .buildPost(Buf.Utf8("""{"packageName":"helloworld", "all":true}"""))
      val uninstallResponse = CosmosClient(uninstallRequest)
      assertResult(Status.Ok)(uninstallResponse.status)
      assertResult(MediaTypes.UninstallResponse.show)(uninstallResponse.headerMap("Content-Type"))
    }

    "error when multiple packages are installed and no appId is specified and all isn't set" in {
      // install 'helloworld' twice
      val appId1 = UUID.randomUUID()
      val installBody1 = s"""{"packageName":"helloworld", "appId":"$appId1"}"""
      val installRequest1 = CosmosClient.requestBuilder("package/install")
        .addHeader("Content-Type", MediaTypes.InstallRequest.show)
        .addHeader("Accept", MediaTypes.InstallResponse.show)
        .buildPost(Buf.Utf8(installBody1))
      val installResponse1 = CosmosClient(installRequest1)
      assertResult(Status.Ok, s"install failed: $installBody1")(installResponse1.status)

      val appId2 = UUID.randomUUID()
      val installBody2 = s"""{"packageName":"helloworld", "appId":"$appId2"}"""
      val installRequest2 = CosmosClient.requestBuilder("package/install")
        .addHeader("Content-Type", MediaTypes.InstallRequest.show)
        .addHeader("Accept", MediaTypes.InstallResponse.show)
        .buildPost(Buf.Utf8(installBody2))
      val installResponse2 = CosmosClient(installRequest2)
      assertResult(Status.Ok, s"install failed: $installBody2")(installResponse2.status)

      val uninstallRequest = CosmosClient.requestBuilder("package/uninstall")
        .setHeader("Accept", MediaTypes.UninstallResponse.show)
        .setHeader("Content-Type", MediaTypes.UninstallRequest.show)
        .buildPost(Buf.Utf8("""{"packageName":"helloworld"}"""))
      val uninstallResponse = CosmosClient(uninstallRequest)
      val uninstallResponseBody = uninstallResponse.contentString
      assertResult(Status.BadRequest)(uninstallResponse.status)
      assertResult(MediaTypes.ErrorResponse.show)(uninstallResponse.headerMap("Content-Type"))
      val Xor.Right(err) = decode[ErrorResponse](uninstallResponseBody)
      assertResult(s"Multiple apps named [helloworld] are installed: [/$appId1, /$appId2]")(err.message)
    }
  }

}
