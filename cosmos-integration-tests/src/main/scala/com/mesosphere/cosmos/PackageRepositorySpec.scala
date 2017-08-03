package com.mesosphere.cosmos

import cats.data.Ior
import com.mesosphere.cosmos.ItOps._
import com.mesosphere.cosmos.error.IndexNotFound
import com.mesosphere.cosmos.error.RepoNameOrUriMissing
import com.mesosphere.cosmos.error.RepositoryAddIndexOutOfBounds
import com.mesosphere.cosmos.error.RepositoryAlreadyPresent
import com.mesosphere.cosmos.error.RepositoryNotPresent
import com.mesosphere.cosmos.error.RepositoryUriConnection
import com.mesosphere.cosmos.error.UniverseClientHttpError
import com.mesosphere.cosmos.error.UnsupportedContentType
import com.mesosphere.cosmos.error.UnsupportedRepositoryUri
import com.mesosphere.cosmos.error.UnsupportedRepositoryVersion
import com.mesosphere.cosmos.http.TestContext
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.universe.MediaTypes
import com.mesosphere.universe.v2.model.UniverseVersion
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.http.Status
import org.jboss.netty.handler.codec.http.HttpMethod
import org.scalatest.FeatureSpec
import org.scalatest.Matchers

class PackageRepositorySpec extends FeatureSpec with Matchers {
  private[this] implicit val testContext = TestContext.fromSystemProperties()

  feature("The package/repository/list endpoint") {
    scenario("The user should be able to list added repositories") {
      val name = "cli-test-4"
      val uri: Uri = "https://github.com/mesosphere/universe/archive/cli-test-4.zip"
      RoundTrips.withRepository(name, uri).runWith { _ =>
        Requests.listRepositories() should contain(
          rpc.v1.model.PackageRepository(name, uri)
        )
      }
    }
    scenario("The user should not see removed repositories") {
      val name = "cli-test-4"
      val uri: Uri = "https://github.com/mesosphere/universe/archive/cli-test-4.zip"
      (RoundTrips.withRepository(name, uri) &:
        RoundTrips.withDeletedRepository(Some(name), Some(uri))).runWith { _ =>
        Requests.listRepositories() should not contain
          rpc.v1.model.PackageRepository(name, uri)
      }
    }
  }

  feature("The package/repository/add endpoint") {
    scenario("the user would like to add a repository at the default priority (lowest priority)") {
      val name = "cli-test-4"
      val uri: Uri = "https://github.com/mesosphere/universe/archive/cli-test-4.zip"
      RoundTrips.withRepository(name, uri).runWith { response =>
        val last = response.repositories.last
        last.name shouldBe name
        last.uri shouldBe uri
      }
    }
    scenario("the user would like to add a repository at a specific priority") {
      val name = "cli-test-4"
      val uri: Uri = "https://github.com/mesosphere/universe/archive/cli-test-4.zip"
      val index = 0
      RoundTrips.withRepository(name, uri, Some(index)).runWith { response =>
        val actual = response.repositories(index)
        actual.name shouldBe name
        actual.uri shouldBe uri
      }
    }
    scenario("the user should be able to add a repository at the end of the list using an index") {
      val name = "bounds"
      val uri: Uri = "https://github.com/mesosphere/universe/archive/cli-test-4.zip"
      val index = Requests.listRepositories().size
      RoundTrips.withRepository(name, uri, Some(index)).runWith { response =>
        val last = response.repositories.last
        last.name shouldBe name
        last.uri shouldBe uri
      }
    }
    scenario("the user should receive an error when trying to add a duplicated repository") {
      val name = "cli-test-4"
      val uri: Uri = "https://github.com/mesosphere/universe/archive/cli-test-4.zip"
      val expectedError = RepositoryAlreadyPresent(Ior.Both(name, uri)).as[ErrorResponse]
      RoundTrips.withRepository(name, uri).runWith { _ =>
        val error = intercept[HttpErrorResponse] {
          RoundTrips.withRepository(name, uri).run()
        }
        error.status shouldBe Status.BadRequest
        error.errorResponse shouldBe expectedError
      }
    }
    scenario("the user should receive an error when trying to add a repository out of bounds") {
      val name = "bounds"
      val uri: Uri = "https://github.com/mesosphere/universe/archive/cli-test-4.zip"
      val index = Int.MaxValue
      val max = Requests.listRepositories().size
      val expectedError = RepositoryAddIndexOutOfBounds(index, max).as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withRepository(name, uri, Some(index)).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
    scenario("Issue #204: the user should receive an error " +
      "when trying to add a repository with a broken uri") {
      val repo = rpc.v1.model.PackageRepository("unreachable", "http://fake.fake")
      val expectedError = RepositoryUriConnection(
        repo,
        repo.uri.toString.stripPrefix("http://")
      ).as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withRepository(repo.name, repo.uri).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
    scenario("Issue #219: the user should receive an error when trying to add a repository " +
      "with a bad file layout") {
      // TODO: Use a more reliable URI
      val name = "invalid"
      val uri: Uri = "https://github.com/mesosphere/dcos-cli/archive/master.zip"
      val expectedError = IndexNotFound(uri).as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withRepository(name, uri).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
    scenario("Issue #219: the user should receive an error when trying to add a repository " +
      "that is a non-zip-encoded repository bundle") {
      // TODO: Use a more reliable URI
      val name = "invalid"
      val uri: Uri = "https://mesosphere.com/"
      val expectedError = UnsupportedContentType(
        List(MediaTypes.UniverseV4Repository,
          MediaTypes.UniverseV3Repository,
          MediaTypes.UniverseV2Repository),
        Some("text/html;charset=utf-8")
      ).as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withRepository(name, uri).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
    scenario("the user should receive an error when trying to add a non-repository") {
      val name = "not-repository"
      val uri: Uri = "https://www.mesosphere.com/uontehusantoehusanth"
      val expectedError = UniverseClientHttpError(
        PackageRepository(name, uri),
        HttpMethod.GET,
        Status.NotFound
      ).exception(Status.BadRequest).errorResponse
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withRepository(name, uri).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
    scenario("Issue #209: the user must receive an error " +
      "when adding an unsupported repository version") {
      val name = "old-versioned-repository"
      val uri: Uri = "https://github.com/mesosphere/universe/archive/version-1.x.zip"
      val version = UniverseVersion("1.0.0-rc1")
      val expectedError = UnsupportedRepositoryVersion(version).as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withRepository(name, uri).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
    scenario("the user must receive an error when trying to add " +
      "a repository with an unsupported protocol") {
      val name = "unsupported"
      val uri: Uri = "file://foo/bar"
      val expectedError = UnsupportedRepositoryUri(uri).as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withRepository(name, uri).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
  }

  feature("The package/repository/delete endpoint") {
    scenario("the user would like to delete a repository by name") {
      val deleted = Requests.listRepositories().head
      RoundTrips.withDeletedRepository(Some(deleted.name)).runWith { dr =>
        dr.repositories should not contain deleted
        Requests.listRepositories() should not contain deleted
      }
    }
    scenario("the user would like to delete a repository by uri") {
      val deleted = Requests.listRepositories().head
      RoundTrips.withDeletedRepository(uri = Some(deleted.uri)).runWith { dr =>
        dr.repositories should not contain deleted
        Requests.listRepositories() should not contain deleted
      }
    }
    scenario("Issue #200: the user should receive an" +
      " error when trying to delete a non-existent repository") {
      val name = "does-not-exist"
      val expectedError = RepositoryNotPresent(Ior.Left(name)).as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withDeletedRepository(Some(name)).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
    scenario("the user should receive an error when nether name nor uri are specified") {
      val expectedError = RepoNameOrUriMissing().as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withDeletedRepository().run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
    scenario("the user should receive an error when trying to delete " +
      "a repository whose name exists but uri does not match") {
      val repo = Requests.listRepositories().head
      val badUri: Uri = "http://www.mesosphere.com/auntoheust"
      val expectedError = RepositoryNotPresent(
        Ior.Both(repo.name, badUri)
      ).as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withDeletedRepository(Some(repo.name), Some(badUri)).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
  }

}
