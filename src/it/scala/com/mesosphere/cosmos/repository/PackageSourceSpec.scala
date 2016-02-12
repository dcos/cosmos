package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.handler.PackageRepositoryAddHandler
import com.mesosphere.cosmos.handler.PackageRepositoryDeleteHandler
import com.mesosphere.cosmos.handler.PackageRepositoryListHandler
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.{UnitSpec, ZooKeeperFixture}
import com.netaporter.uri.Uri
import com.twitter.io.Charsets
import com.twitter.util.Await
import com.twitter.util.Future
import io.circe.Json
import io.finch.circe._

final class PackageSourceSpec extends UnitSpec with ZooKeeperFixture {

  import PackageSourceSpec._

  "List sources endpoint" in {
    withZooKeeperClient { client =>
      val sourcesStorage = new ZooKeeperStorage(client, SourceVersion2x.uri)
      val listSourceHandler = new PackageRepositoryListHandler(sourcesStorage)
      val request = PackageRepositoryListRequest()

      assertResult(
        PackageRepositoryListResponse(List(SourceVersion2x))
      )(
        Await.result(listSourceHandler(request))
      )
    }
  }

  // TODO cruhland list-sources: Test for missing ZooKeeper package-sources/v1 node
  // TODO cruhland list-sources: Test for invalid JSON data for package-sources
  // TODO cruhland list-sources: Test for URIs with invalid syntax in the package sources list
  // TODO cruhland list-sources: Test for URIs that don't resolve to repo bundles in the sources list
  // TODO cruhland list-sources: Test for sources that don't have distinct names

  "Add source endpoint" in {
    withZooKeeperClient { client =>
      val sourcesStorage = new ZooKeeperStorage(client, SourceVersion2x.uri)
      val deleteSourceHandler = new PackageRepositoryDeleteHandler(sourcesStorage)
      val addSourceHandler = new PackageRepositoryAddHandler(sourcesStorage)
      val listSourceHandler = new PackageRepositoryListHandler(sourcesStorage)

      forAll (PackageRepositoryAddScenarios) { scenario =>
        setStorageState(listSourceHandler, addSourceHandler, deleteSourceHandler, Nil)

        scenario.foreach { assertion =>
          val addResponse = Await.result(addSourceHandler(assertion.request))
          assertResult(PackageRepositoryAddResponse(assertion.responseList))(addResponse)
        }
      }
    }
  }

  // TODO cruhland add-source: Test for failure on concurrent update
  // TODO cruhland add-source: Test for failure on ZK node missing, or other ZK error

  "Delete source endpoint" in {
    withZooKeeperClient { client =>
      val sourcesStorage = new ZooKeeperStorage(client, SourceVersion2x.uri)
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
}

private[cosmos] object PackageSourceSpec extends UnitSpec {

  private[cosmos] val SourceVersion2x = PackageRepository(
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

  private val PackageRepositoryAddScenarios = Table(
    "scenario",
    List(AddSourceAssertion(addRequest(SourceVersion2x, None), List(SourceVersion2x))),
    List(AddSourceAssertion(addRequest(SourceCliTest4, None), List(SourceCliTest4))),
    List(AddSourceAssertion(addRequest(SourceVersion2x, None), List(SourceVersion2x)),
      AddSourceAssertion(addRequest(SourceCliTest4, None), List(SourceCliTest4, SourceVersion2x))),
    List(AddSourceAssertion(addRequest(SourceCliTest4, None), List(SourceCliTest4)),
      AddSourceAssertion(addRequest(SourceVersion2x, None), List(SourceVersion2x, SourceCliTest4))),
    List(AddSourceAssertion(addRequest(SourceMesosphere, Some(0)), List(SourceMesosphere)),
      AddSourceAssertion(addRequest(SourceVersion2x, Some(1)),
        List(SourceMesosphere, SourceVersion2x)),
      AddSourceAssertion(addRequest(SourceCliTest4, Some(0)),
        List(SourceCliTest4, SourceMesosphere, SourceVersion2x)),
      AddSourceAssertion(addRequest(SourceExample, Some(2)),
        List(SourceCliTest4, SourceMesosphere, SourceExample, SourceVersion2x)))
  )

  // TODO cruhland: Adding sources with duplicate names
  // TODO cruhland: Adding sources with duplicate URIs
  // TODO cruhland: Adding sources at indices outside the list boundaries
  // TODO cruhland: Adding names or URIs with invalid values
  // TODO cruhland: Adding URIs that don't resolve

  private val PackageRepositoryDeleteScenarios = Table(
    ("starting value", "delete scenario"),
    (List(SourceVersion2x), List(DeleteSourceAssertion(deleteRequestByName(SourceVersion2x), Nil))),
    (List(SourceVersion2x), List(DeleteSourceAssertion(deleteRequestByUri(SourceVersion2x), Nil))),
    (List(SourceCliTest4), List(DeleteSourceAssertion(deleteRequestByName(SourceCliTest4), Nil))),
    (List(SourceCliTest4), List(DeleteSourceAssertion(deleteRequestByUri(SourceCliTest4), Nil))),
    (List(SourceVersion2x, SourceCliTest4),
      List(DeleteSourceAssertion(deleteRequestByName(SourceVersion2x), List(SourceCliTest4)),
        DeleteSourceAssertion(deleteRequestByName(SourceCliTest4), Nil))),
    (List(SourceVersion2x, SourceCliTest4),
      List(DeleteSourceAssertion(deleteRequestByName(SourceCliTest4), List(SourceVersion2x)),
        DeleteSourceAssertion(deleteRequestByName(SourceVersion2x), Nil))),
    (List(SourceMesosphere, SourceCliTest4, SourceExample, SourceVersion2x),
      List(
        DeleteSourceAssertion(deleteRequestByUri(SourceExample),
          List(SourceMesosphere, SourceCliTest4, SourceVersion2x)),
        DeleteSourceAssertion(deleteRequestByUri(SourceCliTest4),
          List(SourceMesosphere, SourceVersion2x)),
        DeleteSourceAssertion(deleteRequestByUri(SourceMesosphere), List(SourceVersion2x)),
        DeleteSourceAssertion(deleteRequestByUri(SourceVersion2x), Nil)))
  )

  // TODO cruhland: Deleting source that is not present
  // TODO cruhland: Delete source request with neither name nor URI
  // TODO cruhland: Delete source request with name and URI

  private[this] def addRequest(
    source: PackageRepository,
    index: Option[Int]
  ): PackageRepositoryAddRequest = {
    PackageRepositoryAddRequest(source.name, source.uri, index)
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
}

private case class AddSourceAssertion(
  request: PackageRepositoryAddRequest,
  responseList: List[PackageRepository]
)

private case class DeleteSourceAssertion(
  request: PackageRepositoryDeleteRequest,
  responseList: List[PackageRepository]
)
