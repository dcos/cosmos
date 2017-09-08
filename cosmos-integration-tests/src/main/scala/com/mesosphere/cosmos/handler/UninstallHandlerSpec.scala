package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.MarathonAppNotFound
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.service.ServiceUninstaller
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.InstallRequest
import com.mesosphere.cosmos.rpc.v1.model.UninstallRequest
import com.mesosphere.cosmos.rpc.v1.model.UninstallResponse
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.model.PackageDetailsVersion
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.util.Await
import io.circe.Json
import io.circe.JsonObject
import io.circe.jawn._
import java.util.UUID
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import scala.util.Right

final class UninstallHandlerSpec extends FreeSpec with Eventually with SpanSugar {

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
      val Some(data) = err.data
      val Right(appIds) = Json.fromJsonObject(data).hcursor.get[Set[AppId]]("appIds")
      assertResult(Set(appId1, appId2))(appIds)
      val cleanupRequest = UninstallRequest("helloworld", appId = None, all = Some(true))
      val cleanupResponse = submitUninstallRequest(cleanupRequest)
      assertResult(Status.Ok)(cleanupResponse.status)
    }
  }

  "SDK Based services use custom uninstall behavior" - {

    "Be able to uninstall a service in the middle of a Marathon deploy" in {
      val installResponse = installHelloWorld()
      assertResult(Status.Ok)(installResponse.status)

      // Immediately turn around and try to uninstall it, while it's still being initially deployed.
      assertUninstallRequest(HelloWorldPackageName, Some(HelloWorldAppId), "uninstall")

      waitUntilDeleted(HelloWorldAppId, "wait")
    }

    "DCOS-17237: cancel pending uninstalls if their Marathon apps get deleted" in {
      val installResponseOne = installHelloWorld()
      assertResult(Status.Ok)(installResponseOne.status)

      waitUntilDeployed(HelloWorldAppId, "deploy 1")

      // Kick off two background uninstall tasks; the second should stop when the app is deleted
      assertUninstallRequest(HelloWorldPackageName, appId = Some(HelloWorldAppId), "uninstall 1")
      assertUninstallRequest(HelloWorldPackageName, appId = Some(HelloWorldAppId), "uninstall 2")
      waitUntilDeleted(HelloWorldAppId, "delete 1")

      // Give the second task time to notice the app was deleted
      waitForServiceUninstaller()

      // Reinstall; this will detect whether the second task has actually stopped as intended
      val installResponseTwo = installHelloWorld()

      try {
        assertResult(Status.Ok)(installResponseTwo.status)

        waitUntilDeployed(HelloWorldAppId, "deploy 2")
        waitForServiceUninstaller()
      } finally {
        // This will fail if the app was already deleted
        assertUninstallRequest(HelloWorldPackageName, appId = Some(HelloWorldAppId), "cleanup")

        // Since hello-world is an SDK package, we must wait until the app is actually gone
        val _ = waitUntilDeleted(HelloWorldAppId, "delete 2")
      }
    }

    "DCOS-17237: avoid uninstalling the app if its Mesos framework ID has changed" in {
      val firstInstallResponse = installHelloWorld()
      assertResult(Status.Ok)(firstInstallResponse.status)

      try {
        waitUntilDeployed(HelloWorldAppId, "deploy 1")

        assertUninstallRequest(HelloWorldPackageName, appId = Some(HelloWorldAppId), "uninst 1")
        assertUninstallRequest(HelloWorldPackageName, appId = Some(HelloWorldAppId), "uninst 2")
        // Wait until app is deleted, but don't wait for the second task to notice
        waitUntilDeleted(HelloWorldAppId, "deploy 2")

        // Reinstall the app
        val secondInstallResponse = installHelloWorld()
        assertResult(Status.Ok)(secondInstallResponse.status)

        // Wait until SDK is available, plus uninstaller delay; confirm app still exists
        waitUntilDeployed(HelloWorldAppId, "deploy 3")
        waitForServiceUninstaller()
      } finally {
        // This will fail if the app was already deleted
        assertUninstallRequest(HelloWorldPackageName, appId = Some(HelloWorldAppId), "uninst 3")

        // Since hello-world is an SDK package, we must wait until the app is actually gone
        val _ = waitUntilDeleted(HelloWorldAppId, "delete")
      }
    }

  }

  def waitUntilDeployed(appId: AppId, clue: Any): Assertion = {
    eventually(timeout(2.minutes), interval(10.seconds)) {
      // TODO Get the CommonsVersionLabel ("v1") from the Marathon app, instead of hardcoding
      val version = Await.result(adminRouter.getApp(appId).map(_.app.labels.getOrElse(UninstallHandler.SdkVersionLabel, "v1")))
      val statusFuture = adminRouter.getSdkServicePlanStatus(appId, version, "deploy")

      assertResult(Status.Ok, clue)(Await.result(statusFuture).status)
    }
  }

  def assertUninstallRequest(packageName: String, appId: Option[AppId], clue: Any): Assertion = {
    val uninstallRequest = UninstallRequest(packageName, appId, Some(false))
    val uninstallResponse = submitUninstallRequest(uninstallRequest)
    assertResult(Status.Ok, clue)(uninstallResponse.status)

    assertResult(MediaTypes.UninstallResponse.show, clue) {
      uninstallResponse.headerMap(Fields.ContentType)
    }
  }

  def waitUntilDeleted(appId: AppId, clue: Any): Assertion = {
    eventually(timeout(2.minutes), interval(10.seconds)) {
      val exception = intercept[CosmosException](Await.result(adminRouter.getApp(appId)))

      assert(exception.error.isInstanceOf[MarathonAppNotFound], clue)
    }
  }

}

object UninstallHandlerSpec {

  val HelloWorldPackageName: String = "hello-world"
  val HelloWorldAppId: AppId = AppId(HelloWorldPackageName)

  // Ensure only one "world" pod is launched, to avoid resource starvation
  val HelloWorldOptions: JsonObject = {
    JsonObject.singleton(
      "world",
      Json.fromJsonObject(JsonObject.singleton("count", Json.fromInt(1)))
    )
  }

  def installHelloWorld(): Response = {
    // Always use the custom options to avoid a stuck install
    val request = InstallRequest(HelloWorldPackageName, options = Some(HelloWorldOptions))
    submitInstallRequest(request)
  }

  def submitInstallRequest(
    installRequest: InstallRequest
  ): Response = {
    CosmosClient.submit(CosmosRequests.packageInstallV1(installRequest))
  }

  def submitUninstallRequest(
    uninstallRequest: UninstallRequest
  ): Response = {
    CosmosClient.submit(CosmosRequests.packageUninstall(uninstallRequest))
  }

  def waitForServiceUninstaller(): Unit = {
    Thread.sleep(2 * ServiceUninstaller.RetryInterval.inMillis)
  }

}
