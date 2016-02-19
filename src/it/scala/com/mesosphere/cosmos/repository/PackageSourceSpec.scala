package com.mesosphere.cosmos.repository

import cats.data.Ior
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.handler.PackageRepositoryAddHandler
import com.mesosphere.cosmos.handler.PackageRepositoryDeleteHandler
import com.mesosphere.cosmos.handler.PackageRepositoryListHandler
import com.mesosphere.cosmos.handler.PackageSearchHandler
import com.mesosphere.cosmos.model._
import com.mesosphere.universe.UniverseVersion
import com.netaporter.uri.Uri
import com.twitter.util._
import io.finch.circe._
import org.scalatest.concurrent.Eventually

final class PackageSourceSpec extends UnitSpec with ZooKeeperFixture with Eventually {

  import PackageSourceSpec._

  "List sources endpoint" in {
    withZooKeeperClient { client =>
      val sourcesStorage = new ZooKeeperStorage(client, UniverseRepository.uri)
      val listSourceHandler = new PackageRepositoryListHandler(sourcesStorage)
      val request = PackageRepositoryListRequest()

      assertResult(
        PackageRepositoryListResponse(List(UniverseRepository))
      )(
        Await.result(listSourceHandler(request))
      )
    }
  }

  "Add source endpoint" - {
    "adding a repository to the default list" in {
      withZooKeeperClient { client =>
        val repositoryStorage = new ZooKeeperStorage(client, UniverseRepository.uri)
        implicit val handler = new PackageRepositoryAddHandler(repositoryStorage)

        assertAdd(List(UniverseRepository, SourceCliTest4), SourceCliTest4)
      }
    }

    "adding repositories at explicit indices" in {
      withZooKeeperClient { client =>
        val repositoryStorage = new ZooKeeperStorage(client, UniverseRepository.uri)
        implicit val handler = new PackageRepositoryAddHandler(repositoryStorage)

        val firstResult = List(UniverseRepository, SourceCliTest4)
        val secondResult = List(SourceMesosphere, UniverseRepository, SourceCliTest4)
        val thirdResult = List(SourceMesosphere, UniverseRepository, SourceExample, SourceCliTest4)

        assertAdd(firstResult, SourceCliTest4, Some(1))
        assertAdd(secondResult, SourceMesosphere, Some(0))
        assertAdd(thirdResult, SourceExample, Some(2))
      }
    }

    "adding duplicate repositories" in {
      withZooKeeperClient { client =>
        val repositoryStorage = new ZooKeeperStorage(client, UniverseRepository.uri)
        implicit val addHandler = new PackageRepositoryAddHandler(repositoryStorage)
        implicit val listHandler = new PackageRepositoryListHandler(repositoryStorage)

        assertAddFailure(
          RepositoryAlreadyPresent(Ior.Both(UniverseRepository.name, UniverseRepository.uri)),
          UniverseRepository
        )

        assertAddFailure(
          RepositoryAlreadyPresent(Ior.Right(UniverseRepository.uri)),
          UniverseRepository.copy(name = SourceCliTest4.name)
        )

        assertAddFailure(
          RepositoryAlreadyPresent(Ior.Left(UniverseRepository.name)),
          UniverseRepository.copy(uri = SourceCliTest4.uri)
        )

        assertAdd(List(UniverseRepository, SourceCliTest4), SourceCliTest4)

        assertAddFailure(
          RepositoryAlreadyPresent(Ior.Both(UniverseRepository.name, SourceCliTest4.uri)),
          UniverseRepository.copy(uri = SourceCliTest4.uri)
        )
      }
    }

  }

  "Delete source endpoint" in {
    withZooKeeperClient { client =>
      val sourcesStorage = new ZooKeeperStorage(client, UniverseRepository.uri)
      val deleteSourceHandler = new PackageRepositoryDeleteHandler(sourcesStorage)
      val addSourceHandler = new PackageRepositoryAddHandler(sourcesStorage)
      val listSourceHandler = new PackageRepositoryListHandler(sourcesStorage)

      forAll(PackageRepositoryDeleteScenarios) { (startingSources, scenario) =>
        setStorageState(listSourceHandler, addSourceHandler, deleteSourceHandler, startingSources)

        scenario.foreach { assertion =>
          val deleteResponse = Await.result(deleteSourceHandler(assertion.request))
          assertResult(PackageRepositoryDeleteResponse(assertion.responseList))(deleteResponse)
        }
      }
    }
  }

  "Issue #209: repository versions must be validated" in {
    IntegrationTests.withTempDirectory { dataDir =>
      withZooKeeperClient { client =>
        val sourcesStorage = new ZooKeeperStorage(client, UniverseRepository.uri)
        val repositories = new MultiRepository(sourcesStorage, dataDir)
        val addRepositoryHandler = new PackageRepositoryAddHandler(sourcesStorage)
        // TODO: Using an external test dependency. Change to something local once the test is working
        val repoUri = Uri.parse("https://github.com/mesosphere/universe/archive/version-1.x.zip")
        val oldVersionRepository = PackageRepository("old-version", repoUri)
        val addRequest = PackageRepositoryAddRequest(oldVersionRepository.name, oldVersionRepository.uri)
        val addResponse = Await.result(addRepositoryHandler(addRequest))
        val expectedList = Seq(UniverseRepository, oldVersionRepository)
        assertResult(expectedList)(addResponse.repositories)

        val searchHandler = new PackageSearchHandler(repositories)
        eventually {
          assertResult(Throw(UnsupportedRepositoryVersion(UniverseVersion("1.0.0-rc1")))) {
            Await.result(searchHandler(SearchRequest(None)).liftToTry)
          }
        }

        // Make sure we always check the version, not just when updating the repo
        // This relies on Cosmos not updating the repo again within a short time window
        assertResult(Throw(UnsupportedRepositoryVersion(UniverseVersion("1.0.0-rc1")))) {
          Await.result(searchHandler(SearchRequest(None)).liftToTry)
        }
      }
    }
  }

}

private[cosmos] object PackageSourceSpec extends UnitSpec {

  private[cosmos] val UniverseRepository = PackageRepository(
    "Universe",
    Uri.parse("https://github.com/mesosphere/universe/archive/version-2.x.zip")
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
    expectedResponseList: List[PackageRepository],
    repository: PackageRepository,
    index: Option[Int] = None
  )(implicit handler: PackageRepositoryAddRequest => Future[PackageRepositoryAddResponse]): Unit = {
    val addRequest = PackageRepositoryAddRequest(repository.name, repository.uri, index)
    val addResponse = Await.result(handler(addRequest))
    assertResult(expectedResponseList)(addResponse.repositories)
  }

  private def assertAddFailure(expectedResponse: CosmosError, repository: PackageRepository)(
    implicit
    addHandler: PackageRepositoryAddRequest => Future[PackageRepositoryAddResponse],
    listHandler: PackageRepositoryListRequest => Future[PackageRepositoryListResponse]
  ): Unit = {
    val repositoriesBeforeAdd = Await.result(listHandler(PackageRepositoryListRequest()))

    assertResult(Throw(expectedResponse)) {
      val addRequest = PackageRepositoryAddRequest(repository.name, repository.uri)
      Await.result(addHandler(addRequest).liftToTry)
    }

    assertResult(repositoriesBeforeAdd.repositories) {
      Await.result(listHandler(PackageRepositoryListRequest())).repositories
    }
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
    lister: (PackageRepositoryListRequest) => Future[PackageRepositoryListResponse],
    adder: (PackageRepositoryAddRequest) => Future[PackageRepositoryAddResponse],
    deleter: (PackageRepositoryDeleteRequest) => Future[PackageRepositoryDeleteResponse],
    state: List[PackageRepository]
  ): Unit = {
    // TODO: not ideal but okay for now

    Await.result(lister(PackageRepositoryListRequest())).repositories.map { repository =>
      Await.result {
        deleter(PackageRepositoryDeleteRequest(uri = Some(repository.uri)))
      }
    }

    state.foreach { repository =>
      Await.result(adder(PackageRepositoryAddRequest(repository.name, repository.uri)))
    }
  }
}

private case class DeleteSourceAssertion(
  request: PackageRepositoryDeleteRequest,
  responseList: List[PackageRepository]
)
