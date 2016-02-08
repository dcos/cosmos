package com.mesosphere.cosmos.handler

import java.nio.file.Files
import java.util.UUID

import cats.data.Xor
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model.{UninstallResponse, AppId}
import com.mesosphere.cosmos.{Cosmos, ErrorResponse, IntegrationSpec}
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.util.Await
import io.circe.parse._

final class UninstallHandlerSpec extends IntegrationSpec {

  val tmpDir = {
    val tempDir = Files.createTempDirectory("cosmos-UninstallHandlerSpec")
    val file = tempDir.toFile
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        if (!com.twitter.io.Files.delete(file)) {
          logger.warn("unable to cleanup temp dir: {}", file.getAbsolutePath)
        }
      }
    })
    val value = file.getAbsolutePath
    logger.info("Setting com.mesosphere.cosmos.dataDir={}", value)
    System.setProperty("com.mesosphere.cosmos.dataDir", value)
    tempDir
  }

  val service = Cosmos.service

  override def createService: Service[Request, Response] = {
    service
  }

  "The uninstall handler" should "be able to uninstall a service" in { service =>
    val installRequest = requestBuilder("package/install")
      .addHeader("Content-Type", MediaTypes.InstallRequest.show)
      .addHeader("Accept", MediaTypes.InstallResponse.show)
      .buildPost(Buf.Utf8("""{"packageName":"cassandra","options":{}}"""))
    val installResponse = service(installRequest)
    val installResponseBody = installResponse.contentString
    logger.info("installResponseBody = {}", installResponseBody)
    assertResult(Status.Ok)(installResponse.status)

    val appId = AppId("cassandra" / "dcos")
    val marathonApp = Await.result(adminRouter.getApp(appId))
    assertResult(appId)(marathonApp.app.id)

    //TODO: Assert framework starts up

    val uninstallRequest = requestBuilder("package/uninstall")
      .setHeader("Accept", MediaTypes.UninstallResponse.show)
      .setHeader("Content-Type", MediaTypes.UninstallRequest.show)
      .buildPost(Buf.Utf8("""{"packageName":"cassandra"}"""))
    val uninstallResponse = service(uninstallRequest)
    val uninstallResponseBody = uninstallResponse.contentString
    logger.info("uninstallResponseBody = {}", uninstallResponseBody)
    assertResult(Status.Ok)(uninstallResponse.status)
    assertResult(MediaTypes.UninstallResponse.show)(uninstallResponse.headerMap("Content-Type"))
    val Xor.Right(body) = decode[UninstallResponse](uninstallResponseBody)
    assert(body.results.flatMap(_.postUninstallNotes).nonEmpty)
  }

  it should "be able to uninstall multiple packages when 'all' is specified" in { service =>
    // install 'helloworld' twice
    val installBody1 = s"""{"packageName":"helloworld", "appId":"${UUID.randomUUID()}"}"""
    val installRequest1 = requestBuilder("package/install")
      .addHeader("Content-Type", MediaTypes.InstallRequest.show)
      .addHeader("Accept", MediaTypes.InstallResponse.show)
      .buildPost(Buf.Utf8(installBody1))
    val installResponse1 = service(installRequest1)
    val installResponse1Body = installResponse1.contentString
    logger.info("installResponse1Body = {}", installResponse1Body)
    assertResult(Status.Ok, s"install failed: $installBody1")(installResponse1.status)

    val installBody2 = s"""{"packageName":"helloworld", "appId":"${UUID.randomUUID()}"}"""
    val installRequest2 = requestBuilder("package/install")
      .addHeader("Content-Type", MediaTypes.InstallRequest.show)
      .addHeader("Accept", MediaTypes.InstallResponse.show)
      .buildPost(Buf.Utf8(installBody2))
    val installResponse2 = service(installRequest2)
    val installResponse2Body = installResponse2.contentString
    logger.info("installResponse2Body = {}", installResponse2Body)
    assertResult(Status.Ok, s"install failed: $installBody2")(installResponse2.status)

    val uninstallRequest = requestBuilder("package/uninstall")
      .setHeader("Accept", MediaTypes.UninstallResponse.show)
      .setHeader("Content-Type", MediaTypes.UninstallRequest.show)
      .buildPost(Buf.Utf8("""{"packageName":"helloworld", "all":true}"""))
    val uninstallResponse = service(uninstallRequest)
    val uninstallResponseBody = uninstallResponse.contentString
    logger.info("uninstallResponseBody = {}", uninstallResponseBody)
    assertResult(Status.Ok)(uninstallResponse.status)
    assertResult(MediaTypes.UninstallResponse.show)(uninstallResponse.headerMap("Content-Type"))
  }

  it should "error when multiple packages are installed and no appId is specified and all isn't set" in { service =>
    // install 'helloworld' twice
    val appId1 = UUID.randomUUID()
    val installBody1 = s"""{"packageName":"helloworld", "appId":"$appId1"}"""
    val installRequest1 = requestBuilder("package/install")
      .addHeader("Content-Type", MediaTypes.InstallRequest.show)
      .addHeader("Accept", MediaTypes.InstallResponse.show)
      .buildPost(Buf.Utf8(installBody1))
    val installResponse1 = service(installRequest1)
    assertResult(Status.Ok, s"install failed: $installBody1")(installResponse1.status)

    val appId2 = UUID.randomUUID()
    val installBody2 = s"""{"packageName":"helloworld", "appId":"$appId2"}"""
    val installRequest2 = requestBuilder("package/install")
      .addHeader("Content-Type", MediaTypes.InstallRequest.show)
      .addHeader("Accept", MediaTypes.InstallResponse.show)
      .buildPost(Buf.Utf8(installBody2))
    val installResponse2 = service(installRequest2)
    assertResult(Status.Ok, s"install failed: $installBody2")(installResponse2.status)

    val uninstallRequest = requestBuilder("package/uninstall")
      .setHeader("Accept", MediaTypes.UninstallResponse.show)
      .setHeader("Content-Type", MediaTypes.UninstallRequest.show)
      .buildPost(Buf.Utf8("""{"packageName":"helloworld"}"""))
    val uninstallResponse = service(uninstallRequest)
    val uninstallResponseBody = uninstallResponse.contentString
    logger.info("uninstallResponseBody = {}", uninstallResponseBody)
    assertResult(Status.BadRequest)(uninstallResponse.status)
    assertResult(MediaTypes.ErrorResponse.show)(uninstallResponse.headerMap("Content-Type"))
    val Xor.Right(err) = decode[ErrorResponse](uninstallResponseBody)
    assertResult(s"Multiple apps named [helloworld] are installed: [/$appId1, /$appId2]")(err.message)
  }

}
