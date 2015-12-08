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

  "cosmos" should "respond to ping" in { f =>
    val request = Request(Method.Get, "/ping")
    val response = f(request)
    assertResult(200)(response.statusCode)
    assertResult("pong")(response.contentString)
  }

  "cosmos" should "accept a Zip file at the import endpoint" in { cosmos =>
    val packageBytes = Buf.ByteArray.Owned(createHelloWorldZip)
    val packagePart = FileElement(name = "file", content = packageBytes)

    val request = RequestBuilder()
      .url("http://localhost:8080/v1/package/import")
      .add(packagePart)
      .buildFormPost(multipart = true)

    val response = cosmos(request)
    assertResult(200)(response.getStatusCode)
    assertResult("Import successful!\n")(response.contentString)
  }

}

object CosmosSpec {

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
