package com.mesosphere.cosmos

import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Paths}
import java.util.zip.{ZipEntry, ZipOutputStream}

import cats.data.Xor
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.twitter.finagle.http.{FileElement, Method, Request, Status}
import com.twitter.io.Buf
import io.circe.parse.parse
import io.circe.syntax._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FreeSpec, Matchers}

final class LocalUniverseSpec extends FreeSpec {

  import LocalUniverseSpec._

  "import endpoint should" ignore {

    "accept a Zip file" in {
      forAll (ValidFilenamePrefixes) { filenamePrefix =>
        val packagePart = FileElement(
          name = "file",
          content = HelloWorldZip,
          contentType = Some("application/zip"),
          filename = Some(s"$filenamePrefix-digest.zip")
        )

        val request = CosmosClient.requestBuilder(ImportEndpoint)
          .add(packagePart)
          .buildFormPost(multipart = true)

        val response = CosmosClient(request)
        assertResult(Status.Ok)(response.status)
        assertSuccessJson("Import successful!")(response.contentString)
      }
    }

    "not allow GET requests" in {
      val request = Request(Method.Get, s"/$ImportEndpoint")
      val response = CosmosClient(request)
      assertResult(Status.NotFound)(response.status)
    }

    "only accept multipart requests" in {
      val request = CosmosClient.requestBuilder(ImportEndpoint)
        .buildPost(HelloWorldZip)

      val response = CosmosClient(request)
      assertResult(Status.BadRequest)(response.status)
    }

    "require a form-data field named 'file' be present" in {
      val request = CosmosClient.requestBuilder(ImportEndpoint)
        .add(FileElement(name = "import", content = HelloWorldZip))
        .buildFormPost(multipart = true)

      val response = CosmosClient(request)
      assertResult(Status.BadRequest)(response.status)
    }

    "require the 'file' field to have an application/zip Content-type" in {
      val request = CosmosClient.requestBuilder(ImportEndpoint)
        .add(FileElement(name = "file", content = HelloWorldZip))
        .buildFormPost(multipart = true)

      val response = CosmosClient(request)
      assertResult(Status.BadRequest)(response.status)
    }

    "require an uploaded filename matching <package>-<version>-<digest>.zip" in {
      forAll (InvalidFilenames) { filename =>
        val packagePart = FileElement(
          name = "file",
          content = HelloWorldZip,
          contentType = Some("application/zip"),
          filename = filename
        )
        val request = CosmosClient.requestBuilder(ImportEndpoint)
          .add(packagePart)
          .buildFormPost(multipart = true)

        val response = CosmosClient(request)
        assertResult(Status.BadRequest)(response.status)
      }
    }

    "require the package to be a non-empty Zip archive" in {
      val fileContents = Table("content", Buf.Empty, HelloWorldPackageJson)
      forAll (fileContents) { content =>
        val packagePart = FileElement(
          name = "file",
          content = content,
          contentType = Some("application/zip"),
          filename = Some("package-1.2.3-digest.zip")
        )
        val request = CosmosClient.requestBuilder(ImportEndpoint)
          .add(packagePart)
          .buildFormPost(multipart = true)

        val response = CosmosClient(request)
        assertResult(Status.BadRequest)(response.status)
      }
    }

  }

}

object LocalUniverseSpec extends Matchers with TableDrivenPropertyChecks  {

  val ImportEndpoint = "package/import"

  lazy val HelloWorldZip: Buf = {
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

    Buf.ByteArray.Owned(baos.toByteArray)
  }

  lazy val HelloWorldPackageJson: Buf = {
    val filePath = Paths.get(getClass.getResource("/helloworld/package.json").toURI)
    Buf.ByteArray.Owned(Files.readAllBytes(filePath))
  }

  val ValidFilenamePrefixes = Table(
    "filename",
    "foo-bar",
    "foo-bar-baz",
    "fee-fie-foe-fum",
    "arangodb-0.2.0",
    "cassandra-0.1.0-1",
    "chronos-2.3.4",
    "kafka-0.9.0-beta",
    "kafka-0.9.2.0",
    "marathon-0.9.0-RC3",
    "marathon-0.10.1",
    "spark-1.4.0-SNAPSHOT",
    "spark-1.5.0-db83ac7",
    "spark-1.5.0-multi-roles-v2",
    "/package-version",
    "foo/package-version",
    "/foo/package-version",
    "foo/bar/package-version",
    "/foo/bar/package-version"
  )

  val InvalidFilenames = Table(
    "filename",
    None,
    Some(""),
    Some("foo"),
    Some(".zip"),
    Some("foo.zip"),
    Some("foo-bar.zip"),
    Some("foo/bar.zip"),
    Some("foo/bar/baz.zip"),
    Some("foo/bar-baz.zip"),
    Some("/.zip"),
    Some("//foo-bar-baz.zip"),
    Some("a//b/foo-bar-baz.zip"),
    Some("--.zip"),
    Some("x--.zip"),
    Some("-x-.zip"),
    Some("--x.zip")
  )

  private def assertSuccessJson(expectedMessage: String)(actualContent: String): Unit = {
    val expected = Map("message" -> expectedMessage).asJson
    val Xor.Right(actual) = parse(actualContent)
    assertResult(expected)(actual)
  }

}
