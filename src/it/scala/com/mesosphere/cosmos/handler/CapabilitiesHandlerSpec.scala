package com.mesosphere.cosmos.handler

import java.nio.file.Files

import cats.data.Xor
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model.{Capability, CapabilitiesResponse}
import com.mesosphere.cosmos.{Cosmos, IntegrationSpec}
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import io.circe.parse._

final class CapabilitiesHandlerSpec extends IntegrationSpec {

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
    file.deleteOnExit()
    val value = file.getAbsolutePath
    logger.info("Setting com.mesosphere.cosmos.universeCacheDir={}", value)
    System.setProperty("com.mesosphere.cosmos.universeCacheDir", value)
    tempDir
  }

  val service = Cosmos.service

  override def createService: Service[Request, Response] = {
    service
  }

  "The capabilites handler" should "be return a document" in { service =>
    val request = requestBuilder("capabilities")
      .setHeader("Accept", MediaTypes.CapabilitiesResponse.show)
      .buildGet()
    val response = service(request)
    val responseBody = response.contentString
    logger.info("responseBody = {}", responseBody)
    assertResult(Status.Ok)(response.status)
    assertResult(MediaTypes.CapabilitiesResponse.show)(response.headerMap("Content-Type"))
    val Xor.Right(body) = decode[CapabilitiesResponse](responseBody)
    assertResult(CapabilitiesResponse(List(Capability("PACKAGE_MANAGEMENT"))))(body)
  }

}
