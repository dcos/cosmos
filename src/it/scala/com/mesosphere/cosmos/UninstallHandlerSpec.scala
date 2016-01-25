package com.mesosphere.cosmos

import java.nio.file.Files
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model.AppId
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.io
import com.twitter.io.Buf
import com.twitter.util.Await

final class UninstallHandlerSpec extends IntegrationSpec {

  val tmpDir = {
    val tempDir = Files.createTempDirectory("cosmos-UninstallHandlerSpec")
    val file = tempDir.toFile
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        if (!io.Files.delete(file)) {
          logger.warn("unable to cleanup temp dir: {}", file.getAbsolutePath)
        }
      }
    })
    file.deleteOnExit()
    val value = file.getAbsolutePath
    logger.info("Setting com.mesosphere.cosmos.universeCacheDir={}", value)
    System.setProperty("com.mesosphere.cosmos.universeCacheDir", value)
    tempDir
  }

  override def createService: Service[Request, Response] = {
    Cosmos.service
  }

  "The uninstall handler" should "be able to uninstall a service" in { service =>
    val installRequest = requestBuilder("v1/package/install")
      .buildPost(Buf.Utf8("""{"packageName":"cassandra","options":{}}"""))
    val installResponse = service(installRequest)
    val installResponseBody = installResponse.contentString
    logger.info("installResponseBody = {}", installResponseBody)
    assertResult(Status.Ok)(installResponse.status)

    val appId = AppId("cassandra" / "dcos")
    val marathonApp = Await.result(adminRouter.getApp(appId))
    assertResult(appId)(marathonApp.app.id)

    //TODO: Assert framework starts up

    val uninstallRequest = requestBuilder("v1/package/uninstall")
      .setHeader("Accept", MediaTypes.UninstallResponse.show)
      .setHeader("Content-Type", MediaTypes.UninstallRequest.show)
      .buildPost(Buf.Utf8("""{"packageName":"cassandra"}"""))
    val uninstallResponse = service(uninstallRequest)
    val uninstallResponseBody = uninstallResponse.contentString
    logger.info("uninstallResponseBody = {}", uninstallResponseBody)
    assertResult(Status.Ok)(uninstallResponse.status)
    assertResult(MediaTypes.UninstallResponse.show)(uninstallResponse.headerMap("Content-Type"))
  }

}
