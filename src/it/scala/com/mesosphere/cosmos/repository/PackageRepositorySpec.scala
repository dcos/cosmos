package com.mesosphere.cosmos.repository

import cats.data.Xor
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient._
import com.mesosphere.cosmos.{UnitSpec, _}
import com.netaporter.uri.Uri
import com.twitter.finagle.http._
import io.circe.parse._
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.Eventually

final class PackageRepositorySpec extends UnitSpec with BeforeAndAfter with Eventually {
  lazy val logger = org.slf4j.LoggerFactory.getLogger(classOf[PackageRepositorySpec])

  import PackageRepositorySpec._

  // TODO: Move all these into the integration spec
  var originalRepositories: Seq[PackageRepository] = null

  before {
    originalRepositories = listRepos().repositories
  }

  after {
    listRepos().repositories.foreach { repo =>
      deleteRepo(PackageRepositoryDeleteRequest(Some(repo.name), Some(repo.uri)))
    }
    originalRepositories.foreach { repo =>
      addRepo(PackageRepositoryAddRequest(repo.name, repo.uri))
    }
  }

  "List sources endpoint" in {
      assertResult(
        PackageRepositoryListResponse(List(UniverseRepository))
      )(
        listRepos()
      )
  }

  "Add source endpoint" - {
    "adding a repository to the default list" in {
      assertAdd(List(UniverseRepository, SourceCliTest4), SourceCliTest4)
    }

    "adding repositories at explicit indices" in {
      val firstResult = List(UniverseRepository, SourceCliTest4)
      val secondResult = List(SourceMesosphere, UniverseRepository, SourceCliTest4)
      val thirdResult = List(SourceMesosphere, UniverseRepository, SourceExample, SourceCliTest4)

      assertAdd(firstResult, SourceCliTest4, Some(1))
      assertAdd(secondResult, SourceMesosphere, Some(0))
      assertAdd(thirdResult, SourceExample, Some(2))
    }

    "adding duplicate repositories" in {
      val n = UniverseRepository.name
      val u = UniverseRepository.uri
      assertAddFailure(
        s"Repository name [$n] and URI [$u] are both already present in the list",
        UniverseRepository
      )

      assertAddFailure(
        s"Repository URI [$u] is already present in the list",
        UniverseRepository.copy(name = SourceCliTest4.name)
      )

      assertAddFailure(
        s"Repository name [$n] is already present in the list",
        UniverseRepository.copy(uri = SourceCliTest4.uri)
      )

      assertAdd(List(UniverseRepository, SourceCliTest4), SourceCliTest4)

      assertAddFailure(
        s"Repository name [$n] and URI [${SourceCliTest4.uri}] are both already present in the list",
        UniverseRepository.copy(uri = SourceCliTest4.uri)
      )
    }
  }

  "Delete source endpoint" in {
    forAll(PackageRepositoryDeleteScenarios) { (startingSources, scenario) =>
      setStorageState(startingSources)

      scenario.foreach { assertion =>
        assertResult(PackageRepositoryDeleteResponse(assertion.responseList))(deleteRepo(assertion.request))
      }
    }
  }

  "Issue #209: repository versions must be validated" in {
    // TODO: Using an external test dependency. Change to something local once the test is working
    val repoUri = Uri.parse("https://github.com/mesosphere/universe/archive/version-1.x.zip")
    val oldVersionRepository = PackageRepository("old-version", repoUri)
    val expectedList = Seq(UniverseRepository, oldVersionRepository)
    val expectedMsg = s"Repository version [1.0.0-rc1] is not supported"
    assertAdd(expectedList, oldVersionRepository)

    def assertUnsupportedVersion(): Unit = {
      val request = CosmosClient.buildPost(
        "package/search",
        SearchRequest(None),
        MediaTypes.SearchRequest,
        MediaTypes.SearchResponse
      )
      val response = CosmosClient(request)
      assertResult(Status.BadRequest)(response.status)
      val Xor.Right(err) = decode[ErrorResponse](response.contentString)
      assertResult(expectedMsg)(err.message)
    }

    eventually { assertUnsupportedVersion() }

    // Make sure we always check the version, not just when updating the repo
    // This relies on Cosmos not updating the repo again within a short time window
    assertUnsupportedVersion()
  }

  "Issue #204: respond with an error when trying to use a repo with a broken URI" - {

    "relative URI" in {
      assertBrokenUri("foobar")
    }

    "absolute URI" in {
      assertBrokenUri("http://foobar")
    }

    "unknown protocol" in {
      assertBrokenUri("cosmos://universe.mesosphere.com/")
    }

    def assertBrokenUri(uriText: String): Unit = {
      val bogusRepository = PackageRepository("bogus", Uri.parse(uriText))
      assertAdd(Seq(UniverseRepository, bogusRepository), bogusRepository)

      val expectedMsg = s"URI for repository [${bogusRepository.name}] is invalid: ${bogusRepository.uri}"

      eventually {
        assertResult(expectedMsg) {
          val request = CosmosClient.buildPost(
            "package/search",
            SearchRequest(None),
            MediaTypes.SearchRequest,
            MediaTypes.SearchResponse
          )
          val response = CosmosClient(request)
          assertResult(Status.BadRequest)(response.status)
          val Xor.Right(err) = decode[ErrorResponse](response.contentString)
          val repo = err.message
          repo
        }
      }
    }
  }
}

private[cosmos] object PackageRepositorySpec extends UnitSpec {

  private[cosmos] val UniverseRepository = PackageRepository(
    "Universe",
    Uri.parse(System.getProperty("com.mesosphere.cosmos.universeBundleUri"))
  )
  private[cosmos] val SourceCliTest4 = PackageRepository(
    "bar",
    Uri.parse("https://github.com/mesosphere/universe/archive/cli-test-4.zip")
  )
  private[cosmos] val SourceMesosphere = PackageRepository(
    "baz",
    Uri.parse("https://mesosphere.com")
  )
  private[cosmos] val SourceExample = PackageRepository(
    "quux",
    Uri.parse("http://example.com")
  )

  private val PackageRepositoryDeleteScenarios = Table(
    ("starting value", "delete scenario"),
    (List(UniverseRepository), List(DeleteSourceAssertion(deleteRequestByName(UniverseRepository), Nil))),
    (List(UniverseRepository), List(DeleteSourceAssertion(deleteRequestByUri(UniverseRepository), Nil))),
    (List(SourceCliTest4), List(DeleteSourceAssertion(deleteRequestByName(SourceCliTest4), Nil))),
    (List(SourceCliTest4), List(DeleteSourceAssertion(deleteRequestByUri(SourceCliTest4), Nil))),
    (List(UniverseRepository, SourceCliTest4),
      List(DeleteSourceAssertion(deleteRequestByName(UniverseRepository), List(SourceCliTest4)),
        DeleteSourceAssertion(deleteRequestByName(SourceCliTest4), Nil))),
    (List(UniverseRepository, SourceCliTest4),
      List(DeleteSourceAssertion(deleteRequestByName(SourceCliTest4), List(UniverseRepository)),
        DeleteSourceAssertion(deleteRequestByName(UniverseRepository), Nil))),
    (List(SourceMesosphere, SourceCliTest4, SourceExample, UniverseRepository),
      List(
        DeleteSourceAssertion(deleteRequestByUri(SourceExample),
          List(SourceMesosphere, SourceCliTest4, UniverseRepository)),
        DeleteSourceAssertion(deleteRequestByUri(SourceCliTest4),
          List(SourceMesosphere, UniverseRepository)),
        DeleteSourceAssertion(deleteRequestByUri(SourceMesosphere), List(UniverseRepository)),
        DeleteSourceAssertion(deleteRequestByUri(UniverseRepository), Nil)))
  )

  private def assertAdd(
    expectedResponseList: Seq[PackageRepository],
    repository: PackageRepository,
    index: Option[Int] = None
  ): Unit = {
    val addRequest = PackageRepositoryAddRequest(repository.name, repository.uri, index)
    assertResult(expectedResponseList)(addRepo(addRequest).repositories)
  }

  private def assertAddFailure(expectedErrorMessage: String, repository: PackageRepository): Unit = {
    val repositoriesBeforeAdd = listRepos()

    assertResult(expectedErrorMessage) {
      val addRequest = PackageRepositoryAddRequest(repository.name, repository.uri)
      val post = CosmosClient.buildPost(
        "package/repository/add",
        addRequest,
        MediaTypes.PackageRepositoryAddRequest,
        MediaTypes.PackageRepositoryAddResponse
      )
      val response = CosmosClient(post)
      assertResult(Status.BadRequest)(response.status)
      val Xor.Right(err) = decode[ErrorResponse](response.contentString)
      err.message
    }

    assertResult(repositoriesBeforeAdd.repositories) {
      listRepos().repositories
    }
  }

  private def addRepo(addRequest: PackageRepositoryAddRequest): PackageRepositoryAddResponse  = {
    CosmosClient.callEndpoint[PackageRepositoryAddRequest, PackageRepositoryAddResponse](
      "package/repository/add",
      addRequest,
      MediaTypes.PackageRepositoryAddRequest,
      MediaTypes.PackageRepositoryAddResponse
    )
  }

  private def deleteRepo(deleteRequest: PackageRepositoryDeleteRequest): PackageRepositoryDeleteResponse  = {
    CosmosClient.callEndpoint[PackageRepositoryDeleteRequest, PackageRepositoryDeleteResponse](
      "package/repository/delete",
      deleteRequest,
      MediaTypes.PackageRepositoryDeleteRequest,
      MediaTypes.PackageRepositoryDeleteResponse
    )
  }

  private def listRepos(): PackageRepositoryListResponse = {
    CosmosClient.callEndpoint[PackageRepositoryListRequest, PackageRepositoryListResponse](
      "package/repository/list",
      PackageRepositoryListRequest(),
      MediaTypes.PackageRepositoryListRequest,
      MediaTypes.PackageRepositoryListResponse
    )
  }

  private[this] def deleteRequestByName(
    source: PackageRepository
  ): PackageRepositoryDeleteRequest = {
    PackageRepositoryDeleteRequest(name = Some(source.name))
  }

  private[this] def deleteRequestByUri(
    source: PackageRepository
  ): PackageRepositoryDeleteRequest = {
    PackageRepositoryDeleteRequest(uri = Some(source.uri))
  }

  private def setStorageState(
    state: List[PackageRepository]
  ): Unit = {
    // TODO: not ideal but okay for now

    listRepos()
      .repositories
      .map { repository =>
        deleteRepo(PackageRepositoryDeleteRequest(uri = Some(repository.uri)))
      }

    state.foreach { repository =>
      addRepo(PackageRepositoryAddRequest(repository.name, repository.uri))
    }
  }
}

private case class DeleteSourceAssertion(
  request: PackageRepositoryDeleteRequest,
  responseList: List[PackageRepository]
)
