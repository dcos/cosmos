package com.mesosphere.cosmos.handler

import cats.data.Ior
import com.mesosphere.cosmos.HttpErrorResponse
import com.mesosphere.cosmos.ItOps._
import com.mesosphere.cosmos.Requests
import com.mesosphere.cosmos.RoundTrips
import com.mesosphere.cosmos.error.RepoNameOrUriMissing
import com.mesosphere.cosmos.error.RepositoryAddIndexOutOfBounds
import com.mesosphere.cosmos.error.RepositoryAlreadyPresent
import com.mesosphere.cosmos.error.RepositoryNotPresent
import com.mesosphere.cosmos.error.RepositoryUriConnection
import com.mesosphere.cosmos.error.UniverseClientHttpError
import com.mesosphere.cosmos.error.UnsupportedContentType
import com.mesosphere.cosmos.error.UnsupportedRepositoryUri
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.universe.MediaTypes
import com.mesosphere.universe.bijection.MediaTypeConversions._
import io.lemonlabs.uri.Uri
import io.lemonlabs.uri.dsl._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.http.Status
import io.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.handler.codec.http.HttpMethod
import org.scalatest.FeatureSpec
import org.scalatest.Matchers

class PackageRepositorySpec extends FeatureSpec with Matchers {

  val repoName = "cassandra-113"
  val mutableUrl: Uri = "http://downloads.mesosphere.com/universe/repo/1.13/package/cassandra.json"

  feature("The package/repository/list endpoint") {
    scenario("The user should be able to list added repositories") {
      RoundTrips.withRepository(repoName, mutableUrl).runWith { _ =>
        Requests.listRepositories() should contain(
          rpc.v1.model.PackageRepository(repoName, mutableUrl)
        )
      }
    }
    scenario("The user should not see removed repositories") {
      (RoundTrips.withRepository(repoName, mutableUrl) &:
        RoundTrips.withDeletedRepository(Some(repoName), Some(mutableUrl))).runWith { _ =>
        Requests.listRepositories() should not contain
          rpc.v1.model.PackageRepository(repoName, mutableUrl)
      }
    }
  }

  feature("The package/repository/add endpoint") {
    scenario("the user would like to add a repository at the default priority (lowest priority)") {
      RoundTrips.withRepository(repoName, mutableUrl).runWith { response =>
        val last = response.repositories.last
        last.name shouldBe repoName
        last.uri shouldBe mutableUrl
      }
    }
    scenario("the user would like to add a repository at a specific priority") {
      val index = 0
      RoundTrips.withRepository(repoName, mutableUrl, Some(index)).runWith { response =>
        val actual = response.repositories(index)
        actual.name shouldBe repoName
        actual.uri shouldBe mutableUrl
      }
    }
    scenario("the user should be able to add a repository at the end of the list using an index") {
      val index = Requests.listRepositories().size
      RoundTrips.withRepository(repoName, mutableUrl, Some(index)).runWith { response =>
        val last = response.repositories.last
        last.name shouldBe repoName
        last.uri shouldBe mutableUrl
      }
    }
    scenario("the user should receive an error when trying to add a duplicated repository") {
      val expectedError = RepositoryAlreadyPresent(Ior.Both(repoName, mutableUrl)).as[ErrorResponse]
      RoundTrips.withRepository(repoName, mutableUrl).runWith { _ =>
        val error = intercept[HttpErrorResponse] {
          RoundTrips.withRepository(repoName, mutableUrl).run()
        }
        error.status shouldBe Status.BadRequest
        error.errorResponse shouldBe expectedError
      }
    }
    scenario("the user should receive an error when trying to add a repository out of bounds") {
      val index = Int.MaxValue
      val max = Requests.listRepositories().size
      val expectedError = RepositoryAddIndexOutOfBounds(index, max).as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withRepository(repoName, mutableUrl, Some(index)).run()
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
      "of unknown media type") {
      // TODO: Use a more reliable URI
      val name = "invalid"
      val uri: Uri = "https://www.google.com/"
      val expectedError = UnsupportedContentType(
        List(MediaTypes.UniverseV4Repository.asCosmos, MediaTypes.UniverseV3Repository.asCosmos),
        Some("text/html;charset=iso-8859-1")
      ).as[ErrorResponse]
      val error = intercept[HttpErrorResponse] {
        RoundTrips.withRepository(name, uri).run()
      }
      error.status shouldBe Status.BadRequest
      error.errorResponse shouldBe expectedError
    }
    scenario("the user should receive an error when trying to add a non-repository") {
      val name = "not-repository"
      val uri: Uri = "https://www.github.com/uontehusantoehusanth"
      val expectedError = rpc.v1.model.ErrorResponse(
        UniverseClientHttpError(
          PackageRepository(name, uri),
          HttpMethod.GET,
          HttpResponseStatus.NOT_FOUND,
          HttpResponseStatus.BAD_REQUEST
        )
      )
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
