package com.mesosphere.cosmos

import java.io.ByteArrayOutputStream
import java.nio.file.{Paths, Files}
import java.util.zip.{ZipEntry, ZipOutputStream}

import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.io.Buf
import io.finch.test.ServiceIntegrationSuite
import org.scalatest.fixture

class CosmosSpec extends fixture.FlatSpec with ServiceIntegrationSuite {

  import CosmosSpec._

  def createService(): Service[Request, Response] = {
    Cosmos.service
  }

  "cosmos ping endpoint" should "respond" in { cosmos =>
    val request = Request(Method.Get, "/ping")
    val response = cosmos(request)
    assertResult(Status.Ok)(response.status)
    assertResult("pong")(response.contentString)
  }

  "cosmos import endpoint" should "accept a Zip file" in { cosmos =>
    val packageBytes = Buf.ByteArray.Owned(createHelloWorldZip)
    val packagePart = FileElement(name = "file", content = packageBytes)

    val request = RequestBuilder()
      .url(s"http://localhost:8080/$importEndpoint")
      .add(packagePart)
      .buildFormPost(multipart = true)

    val response = cosmos(request)
    assertResult(Status.Ok)(response.status)
    assertResult("Import successful!\n")(response.contentString)
  }

  it should "not allow GET requests" in { cosmos =>
    val request = Request(Method.Get, s"importEndpoint")
    val response = cosmos(request)
    assertResult(Status.NotFound)(response.status)
  }

  it should "only accept multipart requests" in { cosmos =>
    val packageBytes = Buf.ByteArray.Owned(createHelloWorldZip)
    val request = RequestBuilder()
      .url(s"http://localhost:8080/$importEndpoint")
      .buildPost(packageBytes)

    val response = cosmos(request)
    assertResult(Status.BadRequest)(response.status)
  }
}

object CosmosSpec {

  val importEndpoint = "v1/package/import"

  def createHelloWorldZip: Array[Byte] = {
    val baos = new ByteArrayOutputStream
    val zos = new ZipOutputStream(baos)

    val files = List("command.json", "config.json", "marathon.json.mustache", "package.json")

    try {
      files.foreach { file =>
        zos.putNextEntry(new ZipEntry(file))
        val filePath = Paths.get("src/test/resources/helloworld/", file)
        val fileBytes = Files.readAllBytes(filePath)
        zos.write(fileBytes)
        zos.closeEntry()
      }
    } finally {
      zos.close()
    }

    baos.toByteArray
  }
}
