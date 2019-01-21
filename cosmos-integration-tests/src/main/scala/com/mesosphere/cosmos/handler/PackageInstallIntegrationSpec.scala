package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.HttpErrorResponse
import com.mesosphere.cosmos.IntegrationBeforeAndAfterAll
import com.mesosphere.cosmos.ItOps._
import com.mesosphere.cosmos.Requests
import com.mesosphere.cosmos.RoundTrips
import com.mesosphere.cosmos.error.PackageAlreadyInstalled
import com.mesosphere.cosmos.error.PackageNotFound
import com.mesosphere.cosmos.error.ServiceMarathonTemplateNotFound
import com.mesosphere.cosmos.error.VersionNotFound
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.thirdparty
import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.http._
import org.scalatest.FeatureSpec
import org.scalatest.Matchers

final class PackageInstallIntegrationSpec extends FeatureSpec with Matchers with IntegrationBeforeAndAfterAll{

  feature("The package/install endpoint") {
    scenario("should store the correct labels") {
      val name = "helloworld"
      val version = "0.1.0"
      val options = """{ "port": 9999 } """.json
      val repoName = "V4TestUniverse"
      RoundTrips.withInstallV1(name, Some(version.detailsVersion), options.asObject).runWith { ir =>
        val repo = Requests.getRepository(repoName)
        val pkg = Requests.describePackage(name, Some(ir.packageVersion)).`package`
        val app = Requests.getMarathonApp(ir.appId)
        app.id shouldBe ir.appId
        app.packageDefinition shouldBe Some(pkg)
        app.packageName shouldBe Some(name)
        app.packageVersion.get.toString shouldBe version
        app.packageRepository.map(_.uri) shouldBe repo.map(_.uri)
        app.serviceOptions shouldBe options.asObject
      }
    }
    scenario("The user should be able to install a package by specifying only the name") {
      val name = "helloworld"
      RoundTrips.withInstallV1(name).runWith { ir =>
        val Some((expectedVersion, _)) = Requests.getHighestReleaseVersion(
          ir.packageName,
          includePackageVersions = true
        )
        ir.packageName shouldBe name
        ir.packageVersion shouldBe expectedVersion
        assert(Requests.isMarathonAppInstalled(ir.appId))
      }
    }
    scenario("The user should be able to install a package with a specific version") {
      val name = "helloworld"
      val version = universe.v2.model.PackageDetailsVersion("0.1.0")
      RoundTrips.withInstallV1(name, Some(version)).runWith { ir =>
        ir.packageName shouldBe name
        ir.packageVersion shouldBe version
        assert(Requests.isMarathonAppInstalled(ir.appId))
      }
    }
    scenario("The user should be able to specify the configuration during install") {
      val name = "helloworld"
      val version = "0.1.0"
      val options = """{ "port": 9999 }""".json
      RoundTrips.withInstallV1(name, Some(version.detailsVersion), options = options.asObject).runWith { ir =>
        val marathonApp = Requests.getMarathonApp(ir.appId)
        marathonApp.serviceOptions shouldBe options.asObject
      }
    }
    scenario("The user should be able to specify the app ID during an install") {
      val name = "helloworld"
      val version = "0.1.0"
      val appId = thirdparty.marathon.model.AppId("utnhaoesntuahs")
      RoundTrips.withInstallV1(name, Some(version.detailsVersion), appId = Some(appId)).runWith { ir =>
        ir.appId shouldBe appId
        assert(Requests.isMarathonAppInstalled(appId))
      }
    }
    scenario("The user should receive an error if trying to install an already installed package") {
      val name = "helloworld"
      val version = "0.1.0"
      RoundTrips.withInstallV1(name, Some(version.detailsVersion)).runWith { _ =>
        val expectedError = PackageAlreadyInstalled().as[ErrorResponse]
        val error = intercept[HttpErrorResponse] {
          RoundTrips.withInstallV1(name, Some(version.detailsVersion)).run()
        }
        error.status shouldBe Status.Conflict
        error.errorResponse shouldBe expectedError
      }
    }
    scenario("The user should recieve an error if trying to install a version that does not exist") {
      val name = "helloworld"
      val version = "0.1.0-does-not-exist"
      val expectedError = VersionNotFound(name, version.version).as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withInstallV1(name, Some(version.detailsVersion)).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
    scenario("The user should receive an error when trying to install a package that does not exist") {
      val name = "does-not-exist"
      val expectedError = PackageNotFound(name).as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withInstallV1(name).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
    scenario("The user should receive an error when trying to install a package with incorrect options") {
      val name = "helloworld"
      // port must be int
      val options =
        """{ "port": "9999" }""".json
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withInstallV1(name, options = options.asObject).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse.`type` shouldBe "JsonSchemaMismatch"
    }
    scenario("The user should receive an error when attempting to install" +
      " a service with no marathon template and requesting a v1 response") {
      val name = "enterprise-security-cli"
      val version = "0.8.0".version
      val expectedError = ServiceMarathonTemplateNotFound(name, version).as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withInstallV1(name).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
    scenario("The user should be able to install a package with no marathon" +
      " template when requesting a v2 response") {
      val name = "enterprise-security-cli"
      val version = "0.8.0"
      // No state change
      val response = Requests.installV2(name, Some(version.detailsVersion))
      val pkg = Requests.describePackage(name, Some(version.detailsVersion))
      response.packageName shouldBe name
      response.packageVersion shouldBe version.version
      response.appId shouldBe None
      response.cli shouldBe pkg.`package`.cli
    }
  }
}
