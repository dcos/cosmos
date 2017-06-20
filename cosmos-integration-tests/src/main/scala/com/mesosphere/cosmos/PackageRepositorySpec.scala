package com.mesosphere.cosmos

import cats.data.Ior
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
import com.mesosphere.cosmos.repository.DefaultRepositories
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.universe.MediaTypes
import com.mesosphere.universe.v2.model.UniverseVersion
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.Status
import org.jboss.netty.handler.codec.http.HttpMethod
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FeatureSpec

class PackageRepositorySpec
  extends FeatureSpec
    with BeforeAndAfter
    with BeforeAndAfterAll {

  private[this] val defaultRepositories = DefaultRepositories().getOrThrow
  private[this] var originalRepositories = Seq.empty[PackageRepository]

  override def beforeAll(): Unit = {
    super.beforeAll()
    originalRepositories = ItUtil.listRepositories()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    ItUtil.replaceRepositoriesWith(originalRepositories)
  }

  before {
    ItUtil.replaceRepositoriesWith(defaultRepositories)
  }

  after {
    ItUtil.replaceRepositoriesWith(originalRepositories)
  }

  feature("The package/repository/list endpoint") {
    scenario("The user should be able to see the currently installed repositories") {
      val currentRepositories = ItUtil.listRepositories()
      assertResult(defaultRepositories)(currentRepositories)
    }
  }

  feature("The package/repository/add endpoint") {
    scenario("the user would like to add a repository at the default priority (lowest priority)") {
      val uri = "https://github.com/mesosphere/universe/archive/cli-test-4.zip"
      val name = "cli-test-4"
      val repository = rpc.v1.model.PackageRepository(name, uri)
      val actual = ItUtil.addRepositoryEither(repository)
      val expected = rpc.v1.model.PackageRepositoryAddResponse(
        originalRepositories :+ repository
      )
      assertResult(Right(expected))(actual)
    }
    scenario("the user would like to add a repository at a specific priority") {
      val uri = "https://github.com/mesosphere/universe/archive/cli-test-4.zip"
      val name = "cli-test-4"
      val index = 0
      val repository = rpc.v1.model.PackageRepository(name, uri)
      val actual = ItUtil.addRepositoryEither(repository, Some(index))
      val expected = rpc.v1.model.PackageRepositoryAddResponse(
        ItUtil.insert(originalRepositories, index, repository)
      )
      assertResult(Right(expected))(actual)
    }
    scenario("the user should receive an error when trying to add a duplicated repository") {
      val repository = defaultRepositories.head.copy(name = "dup")
      val expected = RepositoryAlreadyPresent(
        Ior.Right(repository.uri)
      ).exception.errorResponse
      val actual = ItUtil.addRepositoryEither(repository)
      assertResult(Left(expected))(actual)
      info(expected.message)
    }
    scenario("the user should receive an error when trying to add a repository out of bounds") {
      val uri = "https://github.com/mesosphere/universe/archive/cli-test-4.zip"
      val name = "bounds"
      val index = Int.MaxValue
      val repository = rpc.v1.model.PackageRepository(name, uri)
      val actual = ItUtil.addRepositoryEither(repository, Some(index))
      val expected = RepositoryAddIndexOutOfBounds(
        attempted = index,
        max = defaultRepositories.size - 1
      ).exception.errorResponse
      assertResult(Left(expected))(actual)
      info(expected.message)
    }
    scenario("Issue #204: the user should receive an error when trying to add a repository with a broken uri") {
      val uri = "http://fake.fake"
      val name = "unreachable"
      val repository = rpc.v1.model.PackageRepository(name, uri)
      val actual = ItUtil.addRepositoryEither(repository)
      val expected = RepositoryUriConnection(
        repository = repository,
        cause = uri.stripPrefix("http://")
      ).exception.errorResponse
      assertResult(Left(expected))(actual)
      info(expected.message)
    }
    scenario("Issue #219: the user should receive an error when trying to add a repository " +
      "with a bad file layout") {
      // TODO: Use a more reliable URI
      val name = "invalid"
      val uri = "https://github.com/mesosphere/dcos-cli/archive/master.zip"
      val repository = rpc.v1.model.PackageRepository(name, uri)
      val expected = IndexNotFound(uri).exception.errorResponse
      val actual = ItUtil.addRepositoryEither(repository)
      assertResult(Left(expected))(actual)
      info(expected.message)
    }
    scenario("Issue #219: the user should receive an error when trying to add a repository " +
      "that is a non-zip-encoded repository bundle") {
      // TODO: Use a more reliable URI
      val name = "invalid"
      val uri = "https://mesosphere.com/"
      val repository = rpc.v1.model.PackageRepository(name, uri)
      val expected = UnsupportedContentType(
        List(MediaTypes.UniverseV4Repository,
          MediaTypes.UniverseV3Repository,
          MediaTypes.UniverseV2Repository),
        Some("text/html;charset=utf-8")
      ).exception.errorResponse
      val actual = ItUtil.addRepositoryEither(repository)
      assertResult(Left(expected))(actual)
      info(expected.message)
    }
    scenario("the user should receive an error when trying to add a non-repository") {
      val uri = "https://www.mesosphere.com/uontehusantoehusanth"
      val name = "not-repository"
      val repository = rpc.v1.model.PackageRepository(name, uri)
      val actual = ItUtil.addRepositoryEither(repository)
      val expected = UniverseClientHttpError(
        PackageRepository(name, uri),
        HttpMethod.GET,
        Status.NotFound
      ).exception(Status.BadRequest).errorResponse
      assertResult(Left(expected))(actual)
      info(expected.message)
    }
    scenario("Issue #209: the user must receive an error when adding an unsupported repository version") {
      val name = "old-versioned-repository"
      val uri = "https://github.com/mesosphere/universe/archive/version-1.x.zip"
      val version = UniverseVersion("1.0.0-rc1")
      val repository = rpc.v1.model.PackageRepository(name, uri)
      val expected = UnsupportedRepositoryVersion(version).exception.errorResponse
      val actual = ItUtil.addRepositoryEither(repository)
      assertResult(Left(expected))(actual)
      info(expected.message)
    }
    scenario("the user must receive an error when trying to add " +
      "a repository with an unsupported protocol") {
      val name = "unsupported"
      val uri = "file://foo/bar"
      val repository = rpc.v1.model.PackageRepository(name, uri)
      val expected = UnsupportedRepositoryUri(uri).exception.errorResponse
      val actual = ItUtil.addRepositoryEither(repository)
      assertResult(Left(expected))(actual)
      info(expected.message)
    }
  }

  feature("The package/repository/delete endpoint") {
    scenario("the user would like to delete a repository by name") {
      val deletedRepository = defaultRepositories.head
      val remainingRepositories = defaultRepositories.tail
      val expected = rpc.v1.model.PackageRepositoryDeleteResponse(
        remainingRepositories
      )
      val actual = ItUtil.deleteRepositoryEither(
        name = Some(deletedRepository.name)
      )
      assertResult(Right(expected))(actual)
    }
    scenario("the user would like to delete a repository by uri") {
      val deletedRepository = defaultRepositories.head
      val remainingRepositories = defaultRepositories.tail
      val expected = rpc.v1.model.PackageRepositoryDeleteResponse(
        remainingRepositories
      )
      val actual = ItUtil.deleteRepositoryEither(uri = Some(deletedRepository.uri))
      assertResult(Right(expected))(actual)
    }
    scenario("Issue #200: the user should receive an error when trying to delete a non-existent repository") {
      val name = "does-not-exist"
      val expected = RepositoryNotPresent(
        Ior.Left(name)
      ).exception.errorResponse
      val actual = ItUtil.deleteRepositoryEither(Some(name))
      assertResult(Left(expected))(actual)
      info(expected.message)

    }
    scenario("the user should receive an error when nether name nor uri are specified") {
      val actual = ItUtil.deleteRepositoryEither()
      val expected = RepoNameOrUriMissing().exception.errorResponse
      assertResult(Left(expected))(actual)
      info(expected.message)
    }
    scenario("the user should receive an error when trying to delete " +
      "a repository whose name exists but uri does not match") {
      val repository = defaultRepositories.head
      val badUri = "http://www.mesosphere.com/auntoheust"
      val expected = RepositoryNotPresent(
        Ior.Both(repository.name, badUri)
      ).exception.errorResponse
      val actual = ItUtil.deleteRepositoryEither(
        name = Some(repository.name),
        uri = Some(badUri)
      )
      assertResult(Left(expected))(actual)
      info(expected.message)
    }
  }

}
