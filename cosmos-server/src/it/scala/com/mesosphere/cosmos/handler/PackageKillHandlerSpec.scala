package com.mesosphere.cosmos.handler

import java.util.UUID
import cats.data.Xor
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.{ErrorResponse, KillResponse}
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.Status
import com.twitter.io.Buf
import com.twitter.util.Await
import io.circe.jawn._
import org.scalatest.FreeSpec

final class PackageKillHandlerSpec extends FreeSpec {

  import CosmosIntegrationTestClient._
  import PackageKillHandlerSpec._

  "The kill handler should" - {
    "be able to kill a service" in {
      val appId = AppId("cassandra" / "kill-test")
      val runRequest = CosmosClient.requestBuilder(RunPath)
        .addHeader("Content-Type", MediaTypes.RunRequest.show)
        .addHeader("Accept", MediaTypes.V1RunResponse.show)
        .buildPost(Buf.Utf8(s"""{"packageName":"cassandra", "appId":"${appId.toString}"}"""))
      val runResponse = CosmosClient(runRequest)
      assertResult(Status.Ok)(runResponse.status)

      val marathonApp = Await.result(adminRouter.getApp(appId))
      assertResult(appId)(marathonApp.app.id)

      //TODO: Assert framework starts up

      val killRequest = CosmosClient.requestBuilder(KillPath)
        .setHeader("Accept", MediaTypes.KillResponse.show)
        .setHeader("Content-Type", MediaTypes.KillRequest.show)
        .buildPost(Buf.Utf8("""{"packageName":"cassandra"}"""))
      val killResponse = CosmosClient(killRequest)
      val killResponseBody = killResponse.contentString
      assertResult(Status.Ok)(killResponse.status)
      assertResult(MediaTypes.KillResponse.show)(killResponse.headerMap("Content-Type"))
      val Xor.Right(body) = decode[KillResponse](killResponseBody)
      assert(body.results.flatMap(_.postUninstallNotes).nonEmpty)
    }

    "be able to kill multiple packages when 'all' is specified" in {
      // run 'helloworld' twice
      val runBody1 = s"""{"packageName":"helloworld", "appId":"${UUID.randomUUID()}"}"""
      val runRequest1 = CosmosClient.requestBuilder(RunPath)
        .addHeader("Content-Type", MediaTypes.RunRequest.show)
        .addHeader("Accept", MediaTypes.V1RunResponse.show)
        .buildPost(Buf.Utf8(runBody1))
      val runResponse1 = CosmosClient(runRequest1)
      assertResult(Status.Ok, s"run failed: $runBody1")(runResponse1.status)

      val runBody2 = s"""{"packageName":"helloworld", "appId":"${UUID.randomUUID()}"}"""
      val runRequest2 = CosmosClient.requestBuilder(RunPath)
        .addHeader("Content-Type", MediaTypes.RunRequest.show)
        .addHeader("Accept", MediaTypes.V1RunResponse.show)
        .buildPost(Buf.Utf8(runBody2))
      val runResponse2 = CosmosClient(runRequest2)
      assertResult(Status.Ok, s"run failed: $runBody2")(runResponse2.status)

      val killRequest = CosmosClient.requestBuilder(KillPath)
        .setHeader("Accept", MediaTypes.KillResponse.show)
        .setHeader("Content-Type", MediaTypes.KillRequest.show)
        .buildPost(Buf.Utf8("""{"packageName":"helloworld", "all":true}"""))
      val killResponse = CosmosClient(killRequest)
      assertResult(Status.Ok)(killResponse.status)
      assertResult(MediaTypes.KillResponse.show)(killResponse.headerMap("Content-Type"))
    }

    "error when multiple packages are running and no appId is specified and all isn't set" in {
      // run 'helloworld' twice
      val appId1 = UUID.randomUUID()
      val runBody1 = s"""{"packageName":"helloworld", "appId":"$appId1"}"""
      val runRequest1 = CosmosClient.requestBuilder(RunPath)
        .addHeader("Content-Type", MediaTypes.RunRequest.show)
        .addHeader("Accept", MediaTypes.V1RunResponse.show)
        .buildPost(Buf.Utf8(runBody1))
      val runResponse1 = CosmosClient(runRequest1)
      assertResult(Status.Ok, s"run failed: $runBody1")(runResponse1.status)

      val appId2 = UUID.randomUUID()
      val runBody2 = s"""{"packageName":"helloworld", "appId":"$appId2"}"""
      val runRequest2 = CosmosClient.requestBuilder(RunPath)
        .addHeader("Content-Type", MediaTypes.RunRequest.show)
        .addHeader("Accept", MediaTypes.V1RunResponse.show)
        .buildPost(Buf.Utf8(runBody2))
      val runResponse2 = CosmosClient(runRequest2)
      assertResult(Status.Ok, s"run failed: $runBody2")(runResponse2.status)

      val killRequest = CosmosClient.requestBuilder(KillPath)
        .setHeader("Accept", MediaTypes.KillResponse.show)
        .setHeader("Content-Type", MediaTypes.KillRequest.show)
        .buildPost(Buf.Utf8("""{"packageName":"helloworld"}"""))
      val killResponse = CosmosClient(killRequest)
      val killResponseBody = killResponse.contentString
      assertResult(Status.BadRequest)(killResponse.status)
      assertResult(MediaTypes.ErrorResponse.show)(killResponse.headerMap("Content-Type"))
      val Xor.Right(err) = decode[ErrorResponse](killResponseBody)
      assertResult(s"Multiple apps named [helloworld] are running: [/$appId1, /$appId2]")(err.message)

      val cleanupRequest = CosmosClient.requestBuilder(KillPath)
        .setHeader("Accept", MediaTypes.KillResponse.show)
        .setHeader("Content-Type", MediaTypes.KillRequest.show)
        .buildPost(Buf.Utf8("""{"packageName":"helloworld", "all":true}"""))
      val cleanupResponse = CosmosClient(cleanupRequest)
      assertResult(Status.Ok)(cleanupResponse.status)
    }
  }

}

object PackageKillHandlerSpec {
  val RunPath = "package/run"
  val KillPath = "package/kill"
}
