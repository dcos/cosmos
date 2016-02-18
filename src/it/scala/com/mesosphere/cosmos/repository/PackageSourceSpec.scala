package com.mesosphere.cosmos.repository

import cats.data.Ior
import com.mesosphere.cosmos.IntegrationTests.RepositoryMetadataOps
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.handler.PackageRepositoryAddHandler
import com.mesosphere.cosmos.handler.PackageRepositoryDeleteHandler
import com.mesosphere.cosmos.handler.PackageRepositoryListHandler
import com.mesosphere.cosmos.handler.PackageSearchHandler
import com.mesosphere.cosmos.model._
import com.netaporter.uri.Uri
import com.twitter.util._
import io.finch.circe._
import org.scalatest.concurrent.Eventually

final class PackageSourceSpec extends UnitSpec with ZooKeeperFixture with Eventually {

  import PackageSourceSpec._

  "List sources endpoint" in {
    IntegrationTests.withTempDirectory { dataDir =>
      withZooKeeperClient { client =>
        val sourcesStorage = new ZooKeeperStorage(client, UniverseRepository.uri)
        val repositories = new MultiRepository(sourcesStorage, dataDir)
        val listSourceHandler = new PackageRepositoryListHandler(repositories)
        val request = PackageRepositoryListRequest()

        assertResult(List(UniverseRepository)) {
          Await.result(listSourceHandler(request)).repositories.map(_.toDescriptor)
        }
      }
    }
  }

  "Add source endpoint" - {
    "adding a repository to the default list" in {
      IntegrationTests.withTempDirectory { dataDir =>
        withZooKeeperClient { client =>
          val repositoryStorage = new ZooKeeperStorage(client, UniverseRepository.uri)
          val repositories = new MultiRepository(repositoryStorage, dataDir)
          implicit val addHandler = new PackageRepositoryAddHandler(repositoryStorage)
          implicit val listHandler = new PackageRepositoryListHandler(repositories)

          assertAdd(List(SourceCliTest4, UniverseRepository), SourceCliTest4)
        }
      }
    }

    "adding repositories at explicit indices" in {
      IntegrationTests.withTempDirectory { dataDir =>
        withZooKeeperClient { client =>
          val repositoryStorage = new ZooKeeperStorage(client, UniverseRepository.uri)
          val repositories = new MultiRepository(repositoryStorage, dataDir)
          implicit val addHandler = new PackageRepositoryAddHandler(repositoryStorage)
          implicit val listHandler = new PackageRepositoryListHandler(repositories)

          val firstResult = List(UniverseRepository, SourceCliTest4)
          val secondResult = List(SourceMesosphere, UniverseRepository, SourceCliTest4)
          val thirdResult = List(SourceMesosphere, UniverseRepository, SourceExample, SourceCliTest4)

          assertAdd(firstResult, SourceCliTest4, Some(1))
          assertAdd(secondResult, SourceMesosphere, Some(0))
          assertAdd(thirdResult, SourceExample, Some(2))
        }
      }
    }

    "adding duplicate repositories" - {
      "fail to add duplicate name and URI" in {
        withHandlers { implicit listHandler => implicit addHandler =>
          assertAddFailure(
            RepositoryAlreadyPresent(Ior.Both(UniverseRepository.name, UniverseRepository.uri)),
            UniverseRepository
          )
        }
      }

      "fail to add duplicate URI" in {
        withHandlers { implicit listHandler => implicit addHandler =>
          assertAddFailure(
            RepositoryAlreadyPresent(Ior.Right(UniverseRepository.uri)),
            UniverseRepository.copy(name = SourceCliTest4.name)
          )
        }
      }

      "fail to add duplicate name" in {
        withHandlers { implicit listHandler => implicit addHandler =>
          assertAddFailure(
            RepositoryAlreadyPresent(Ior.Left(UniverseRepository.name)),
            UniverseRepository.copy(uri = SourceCliTest4.uri)
          )
        }
      }

      "fail to add duplicate name and URI, from separate entries" in {
        withHandlers { implicit listHandler => implicit addHandler =>
          assertAdd(List(SourceCliTest4, UniverseRepository), SourceCliTest4)

          assertAddFailure(
            RepositoryAlreadyPresent(Ior.Both(UniverseRepository.name, SourceCliTest4.uri)),
            UniverseRepository.copy(uri = SourceCliTest4.uri)
          )
        }
      }
    }

  }

  "Delete source endpoint" in {
    IntegrationTests.withTempDirectory { dataDir =>
      withZooKeeperClient { client =>
        val sourcesStorage = new ZooKeeperStorage(client, UniverseRepository.uri)
        val repositories = new MultiRepository(sourcesStorage, dataDir)
        val deleteSourceHandler = new PackageRepositoryDeleteHandler(sourcesStorage)
        val addSourceHandler = new PackageRepositoryAddHandler(sourcesStorage)
        val listSourceHandler = new PackageRepositoryListHandler(repositories)

        forAll(PackageRepositoryDeleteScenarios) { (startingSources, scenario) =>
          setStorageState(listSourceHandler, addSourceHandler, deleteSourceHandler, startingSources)

          scenario.foreach { assertion =>
            val deleteResponse = Await.result(deleteSourceHandler(assertion.request))
            assertResult(PackageRepositoryDeleteResponse(assertion.responseList))(deleteResponse)
          }
        }
      }
    }
  }

  "Issue #209: repository versions must be validated" in {
    IntegrationTests.withTempDirectory { dataDir =>
      withZooKeeperClient { client =>
        // TODO: Using an external test dependency. Change to something local once the test is working
        val repoUri = Uri.parse("https://github.com/mesosphere/universe/archive/version-1.x.zip")
        val expectedErrorMessage = s"Repository version [1.0.0-rc1] is not supported"
        val error = ErrorResponse("UnsupportedRepositoryVersion", expectedErrorMessage)
        val packageRepository = RepositoryMetadata("old-version", repoUri, Unhealthy(error))
        val universeRepository =
          RepositoryMetadata(UniverseRepository.name, UniverseRepository.uri, Healthy)
        val expectedResponseAfterUse =
          PackageRepositoryListResponse(Seq(packageRepository, universeRepository))

        val sourcesStorage = new ZooKeeperStorage(client, UniverseRepository.uri)
        val repositories = new MultiRepository(sourcesStorage, dataDir)
        val addRepositoryHandler = new PackageRepositoryAddHandler(sourcesStorage)
        val listRepositoryHandler = new PackageRepositoryListHandler(repositories)
        val addRequest = PackageRepositoryAddRequest(packageRepository.name, packageRepository.uri)
        ignoreReturnValue(Await.result(addRepositoryHandler(addRequest)))

        val expectedListBeforeUse = Seq(packageRepository.toDescriptor, UniverseRepository)
        eventually {
          val actual = Await.result(listRepositoryHandler(PackageRepositoryListRequest()))
          assertResult(expectedListBeforeUse)(actual.repositories.map(_.toDescriptor))
        }(patienceConfig)

        val searchHandler = new PackageSearchHandler(repositories)
        ignoreReturnValue {
          intercept[UnsupportedRepositoryVersion](Await.result(searchHandler(SearchRequest(None))))
        }

        eventually {
          val actual = Await.result(listRepositoryHandler(PackageRepositoryListRequest()))
          assertResult(expectedResponseAfterUse)(actual)
        }(patienceConfig)
      }
    }
  }

  private[this] def withHandlers(f: ListHandlerFn => AddHandlerFn => Unit): Unit = {
    IntegrationTests.withTempDirectory { dataDir =>
      withZooKeeperClient { client =>
        val repositoryStorage = new ZooKeeperStorage(client, UniverseRepository.uri)
        val repositories = new MultiRepository(repositoryStorage, dataDir)
        val addHandler = new PackageRepositoryAddHandler(repositoryStorage)
        val listHandler = new PackageRepositoryListHandler(repositories)

        f(listHandler)(addHandler)
      }
    }
  }

}

private[cosmos] object PackageSourceSpec extends UnitSpec with Eventually {

  private type ListHandlerFn = PackageRepositoryListRequest => Future[PackageRepositoryListResponse]
  private type AddHandlerFn = PackageRepositoryAddRequest => Future[PackageRepositoryAddResponse]

  private[cosmos] val UniverseRepository = RepositoryDescriptor(
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
  )(implicit
    addHandler: PackageRepositoryAddRequest => Future[PackageRepositoryAddResponse],
    listHandler: PackageRepositoryListRequest => Future[PackageRepositoryListResponse]
  ): Unit = {
    val addRequest = PackageRepositoryAddRequest(repository.name, repository.uri, index)
    val addResponse = Await.result(addHandler(addRequest))
    assertResult(expectedResponseList)(addResponse.repositories)

    eventually {
      assertResult(expectedResponseList) {
        Await.result(listHandler(PackageRepositoryListRequest())).repositories.map(_.toDescriptor)
      }
    }
  }

  private def assertAddFailure(
    expectedResponse: CosmosError,
    repository: PackageRepository
  )(implicit
    addHandler: PackageRepositoryAddRequest => Future[PackageRepositoryAddResponse],
    listHandler: PackageRepositoryListRequest => Future[PackageRepositoryListResponse]
  ): Unit = {
    val repositoriesBeforeAdd = Await.result(listHandler(PackageRepositoryListRequest()))

    assertResult(Throw(expectedResponse)) {
      val addRequest = PackageRepositoryAddRequest(repository.name, repository.uri)
      Await.result(addHandler(addRequest).liftToTry)
    }

    eventually {
      assertResult(repositoriesBeforeAdd.repositories) {
        Await.result(listHandler(PackageRepositoryListRequest())).repositories
      }
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

    state.reverse.foreach { repository =>
      Await.result(adder(PackageRepositoryAddRequest(repository.name, repository.uri)))
    }
  }

  private def ignoreReturnValue(a: Any): Unit = ()

}

private case class DeleteSourceAssertion(
  request: PackageRepositoryDeleteRequest,
  responseList: List[PackageRepository]
)
