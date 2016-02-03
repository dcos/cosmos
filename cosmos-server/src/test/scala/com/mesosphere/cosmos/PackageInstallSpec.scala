package com.mesosphere.cosmos

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

}
