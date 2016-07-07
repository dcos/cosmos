package com.mesosphere.cosmos

import java.nio.ByteBuffer

import cats.data.Xor
import com.mesosphere.cosmos.handler.PackageInstallHandler
import com.mesosphere.universe
import com.netaporter.uri.dsl._
import com.twitter.io.Charsets
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FreeSpec, Matchers}

class PackageInstallSpec extends FreeSpec with Matchers with TableDrivenPropertyChecks {

  "reports an error if the request to Marathon fails" - {

    "with a generic client error" in {

      //TODO: Move these to be unit tests for MarathonPackageRunner that mock the calls to marathon

      // Taken from Marathon API docs
//      val clientErrorStatuses = Table("status", Seq(400, 401, 403, 422).map(Status.fromCode): _*)

      //      forAll (clientErrorStatuses) { status =>
      //        val dcosClient = Service.const(Future.value(Response(status)))
      //        val errorMessage = s"Received response status code ${status.code} from Marathon"
      //        val errorResponse = ErrorResponse("MarathonGenericError", errorMessage)
      //
      //        forAll(UniversePackagesTable) { (expectedResponse, _, _, _) =>
//      val packageName = "options-test"
      //          val reqBody = InstallRequest(packageName, None, Some(optionsJson))
      //          val mustacheTemplate = buildMustacheTemplate(mergedJson)

      //          val packageFiles = PackageFiles(
      //            revision = "0",
      //            sourceUri = Uri.parse("in/memory/source"),
      //            packageJson = PackageDetails(
      //              packagingVersion = PackagingVersion("2.0"),
      //              name = packageName,
      //              version = PackageDetailsVersion("1.2.3"),
      //              maintainer = "Mesosphere",
      //              description = "Testing user options"
      //            ),
      //            marathonJsonMustache = mustacheTemplate,
      //            configJson = Some(buildConfig(Json.fromJsonObject(defaultsJson)))
      //          )
      //          val packages = Map(packageName -> packageFiles)
      //          val packageCache = MemoryPackageCache(packages)
      //          val packageRunner = new RecordingPackageRunner

      //          new PackageInstallHandler(
      //            MemoryPackageCache.apply()
      //          )
      //          apiClient.installPackageAndAssert(
      //            InstallRequest(expectedResponse.packageName),
      //            expectedResult = Failure(Status.InternalServerError, errorResponse),
      //            preInstallState = Anything,
      //            postInstallState = Unchanged
      //          )
      //        }
      //      }
    }

    "with a generic server error" in {
//      val serverErrorStatuses = Table("status", Seq(500, 502, 503, 504).map(Status.fromCode): _*)

      //      forAll (serverErrorStatuses) { status =>
      //        val dcosClient = Service.const(Future.value(Response(status)))
      //        val errorMessage = s"Received response status code ${status.code} from Marathon"
      //        val errorResponse = ErrorResponse("MarathonBadGateway", errorMessage)
      //
      //        forAll(UniversePackagesTable) { (expectedResponse, _, _, _) =>
      //          apiClient.installPackageAndAssert(
      //            InstallRequest(expectedResponse.packageName),
      //            expectedResult = Failure(Status.BadGateway, errorResponse),
      //            preInstallState = Anything,
      //            postInstallState = Unchanged
      //          )
      //        }
      //      }
    }
  }

  def packageDefinition(mustache: String) = {
    internal.model.PackageDefinition(
      packagingVersion = universe.v3.model.V3PackagingVersion,
      name = "testing",
      version = universe.v3.model.PackageDefinition.Version("a.b.c"),
      maintainer = "foo@bar.baz",
      description = "blah",
      releaseVersion = universe.v3.model.PackageDefinition.ReleaseVersion(0),
      marathon = Some(universe.v3.model.Marathon(ByteBuffer.wrap(mustache.getBytes(Charsets.Utf8))))
    )
  }

  "if the labels object from marathon.json.mustache isn't Map[String, String] an error is returned" in {
    val mustache =
      """
        |{
        |  "labels": {
        |    "idx": 0,
        |    "string": "value"
        |  }
        |}
      """.stripMargin

    val pd = packageDefinition(mustache)

    try {
      val Some(json) = PackageInstallHandler.preparePackageConfig(None, None, pd, "http://someplace")

      val _ = for {
        i <- json.hcursor.downField("labels").downField("idx").as[Int]
      } yield {
        i
      }

      fail("expected a CirceError to be thrown")
    } catch {
      case e @ CirceError(err) =>
        assertResult("String: El(DownField(idx),true),El(DownField(labels),true)")(err.getMessage)
    }
  }

  "if the labels object from marathon.json.mustache does not exist, a default empty object is used" in {
    val mustache =
      """
        |{
        |  "env": {
        |    "some": "thing"
        |  }
        |}
      """.stripMargin

    val pd = packageDefinition(mustache)

    val Some(json) = PackageInstallHandler.preparePackageConfig(None, None, pd, "http://someplace")

    val Xor.Right(some) = for {
      i <- json.hcursor.downField("env").downField("some").as[String]
    } yield {
      i
    }

    assertResult("thing")(some)
  }

}
