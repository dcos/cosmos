package com.mesosphere.cosmos.repository

import cats.data.Xor
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient._
import com.mesosphere.universe.common.circe.Encoders._
import com.netaporter.uri.Uri
import com.twitter.finagle.http._
import io.circe.jawn._
import io.circe.syntax._
import io.circe.{Encoder, Json, JsonObject}
import org.scalatest.concurrent.Eventually
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{AppendedClues, BeforeAndAfter, FreeSpec}

final class PackageRepositorySpec
  extends FreeSpec with BeforeAndAfter with Eventually with AppendedClues {

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
        PackageRepositoryListResponse(defaultRepos)
      )(
        listRepos()
      )
  }

  "Add source endpoint" - {
    "adding a repository to the default list" in {
      assertAdd(defaultRepos :+ SourceCliTest4, SourceCliTest4)
    }

    "adding a repository at an explicit indices" - {
      "append" in {
        assertAdd(defaultRepos :+ SourceCliTest4, SourceCliTest4, Some(defaultRepos.size)) // append
      }
      "prepend" in {
        assertAdd(SourceMesosphere +: defaultRepos, SourceMesosphere, Some(0)) // prepend
      }
      "insert" in {
        assertAdd(defaultRepos.head :: SourceExample :: defaultRepos(1) :: Nil, SourceExample, Some(1)) // insert
      }
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

      assertAdd(defaultRepos :+ SourceCliTest4, SourceCliTest4)

      assertAddFailure(
        s"Repository name [$n] and URI [${SourceCliTest4.uri}] are both already present in the list",
        UniverseRepository.copy(uri = SourceCliTest4.uri)
      )
    }
  }

  "Delete source endpoint" - {
    "should pass all scenarios" in {
      forAll(PackageRepositoryDeleteScenarios) { (startingSources, scenario) =>
        setStorageState(startingSources)

        scenario.foreach { assertion =>
          assertResult(Xor.Right(PackageRepositoryDeleteResponse(assertion.responseList))) {
            deleteRepo(assertion.request)
          }
        }
      }
    }

    "should respond with an error when neither name nor uri are specified" in {
      val Xor.Left(errorResponse) = deleteRepo(PackageRepositoryDeleteRequest(), status = Status.BadRequest)
      assertResult(classOf[RepoNameOrUriMissing].getSimpleName)(errorResponse.`type`)
    }
  }

  "Issue #209: repository versions must be validated" in {
    // TODO: Using an external test dependency. Change to something local once the test is working
    val repoUri = Uri.parse("https://github.com/mesosphere/universe/archive/version-1.x.zip")
    val oldVersionRepository = PackageRepository("old-version", repoUri)
    val expectedList = defaultRepos :+ oldVersionRepository
    val expectedMsg = s"Repository version [1.0.0-rc1] is not supported"
    assertAdd(expectedList, oldVersionRepository)

    def assertUnsupportedVersion(): Unit = {
      val response = searchPackages(SearchRequest(None))
      assertResult(Status.BadRequest)(response.status)
      val Xor.Right(err) = decode[ErrorResponse](response.contentString)
      assertResult(expectedMsg)(err.message)
    }

    eventually { assertUnsupportedVersion() }

    // Make sure we always check the version, not just when updating the repo
    // This relies on Cosmos not updating the repo again within a short time window
    assertUnsupportedVersion()
  }

  "Package repo should should not add unsupported uri protocols" - {
    "file" in {
      val uri = Uri.parse("file://foo/bar")
      val bogusRepository = PackageRepository("foobar", uri)
      assertAddFailure(unsupportedUriMsg(uri), bogusRepository)
    }
    "no scheme" in {
      val uri = Uri.parse("foobar")
      val bogusRepository = PackageRepository("foobar", uri)
      assertAddFailure(unsupportedUriMsg(uri), bogusRepository)
    }
    def unsupportedUriMsg(uri: Uri): String = {
      s"Repository URI [$uri] uses an unsupported scheme. Only http and https are supported"
    }
  }

  "Issue #204: respond with an error when trying to use a repo with a broken URI" - {

    "absolute URI" in {
      assertBrokenUri("http://foobar")
    }

    def assertBrokenUri(uriText: String): Unit = {
      val bogusRepository = PackageRepository("bogus", Uri.parse(uriText))
      assertAdd(defaultRepos :+ bogusRepository, bogusRepository)

      eventually {
        val response = searchPackages(SearchRequest(None))
        assertResult(Status.BadRequest)(response.status)
        assert(decode[ErrorResponse](response.contentString).isRight)
      }
    }
  }

  "Issue #219: respond with an error when a repo URI does not resolve to a valid repo" - {

    "bad file layout" in {
      // TODO: Use a more reliable URI
      val uriText = "https://github.com/mesosphere/dcos-cli/archive/master.zip"
      val expectedMsg = s"Index file missing for repo [$uriText]"
      assertInvalidRepo(uriText, expectedMsg)
    }

    "non-Zip-encoded repo bundle" in {
      // TODO: Use a more reliable URI
      val uriText = "https://mesosphere.com/"
      val contentTypes =
        "application/vnd.dcos.universe.repo+json;charset=utf-8;version=v3, application/zip"
      val expectedMsg = s"Unsupported Content-Type: text/html;charset=utf-8 Accept: [$contentTypes]"
      assertInvalidRepo(uriText, expectedMsg)
    }

    def assertInvalidRepo(uriText: String, expectedMsg: String): Unit = {
      def assertBadRequest(): Unit = {
        val response = searchPackages(SearchRequest(None))
        assertResult(Status.BadRequest)(response.status)
        val Xor.Right(err) = decode[ErrorResponse](response.contentString)
        assertResult(expectedMsg)(err.message)
      }

      val invalidRepo = PackageRepository("invalid", Uri.parse(uriText))
      assertAdd(defaultRepos :+ invalidRepo, invalidRepo)

      eventually { assertBadRequest() }

      assertBadRequest()
    }

  }

  "Issue #200: respond with an error when repo delete has no effect" - {

    "by name only" in {
      val badName = "doesnotexist"
      val request = PackageRepositoryDeleteRequest(name = Some(badName))

      assertDeleteAbsentRepo(request)
    }

    "by uri only" in {
      val badUri = Uri.parse("/not/a/repo/uri")
      val request = PackageRepositoryDeleteRequest(uri = Some(badUri))

      assertDeleteAbsentRepo(request)
    }

    "by name and uri" in {
      val badName = "nonrepo"
      val badUri = Uri.parse("/non/repo")
      val request = PackageRepositoryDeleteRequest(name = Some(badName), uri = Some(badUri))

      assertDeleteAbsentRepo(request)
    }

    "by valid name and invalid uri" in {
      val validName = UniverseRepository.name
      val badUri = Uri.parse("/not/universe")
      val request = PackageRepositoryDeleteRequest(name = Some(validName), uri = Some(badUri))

      assertDeleteAbsentRepo(request)
    }

    "by invalid name and valid uri" in {
      val badName = "notuniverse"
      val validUri = UniverseRepository.uri
      val request = PackageRepositoryDeleteRequest(name = Some(badName), uri = Some(validUri))

      assertDeleteAbsentRepo(request)
    }

    def assertDeleteAbsentRepo(request: PackageRepositoryDeleteRequest): Unit = {
      val Xor.Left(errorResponse) =
        deleteRepo(request, status = Status.BadRequest) withClue "when deleting a repo"
      val errorData = optionToJsonMap("name", request.name) ++ optionToJsonMap("uri", request.uri)

      assertResult("RepositoryNotPresent")(errorResponse.`type`)
      assertResult(Some(JsonObject.fromMap(errorData)))(errorResponse.data)
    }

    def optionToJsonMap[A : Encoder](key: String, aOpt: Option[A]): Map[String, Json] = {
      aOpt.map(a => Map(key -> a.asJson)).getOrElse(Map.empty)
    }

  }

}

object PackageRepositorySpec extends FreeSpec with TableDrivenPropertyChecks {

  private val defaultRepos = DefaultRepositories().getOrThrow
  private[cosmos] val UniverseRepository = defaultRepos.head

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
    val Xor.Right(response) = CosmosClient.callEndpoint[PackageRepositoryAddRequest, PackageRepositoryAddResponse](
      "package/repository/add",
      addRequest,
      MediaTypes.PackageRepositoryAddRequest,
      MediaTypes.PackageRepositoryAddResponse
    )
    response
  }

  private def deleteRepo(
    deleteRequest: PackageRepositoryDeleteRequest,
    status: Status = Status.Ok
  ): Xor[ErrorResponse, PackageRepositoryDeleteResponse]  = {
    CosmosClient.callEndpoint[PackageRepositoryDeleteRequest, PackageRepositoryDeleteResponse](
      "package/repository/delete",
      deleteRequest,
      MediaTypes.PackageRepositoryDeleteRequest,
      MediaTypes.PackageRepositoryDeleteResponse,
      status
    )
  }

  private def listRepos(): PackageRepositoryListResponse = {
    val Xor.Right(response) = CosmosClient.callEndpoint[PackageRepositoryListRequest, PackageRepositoryListResponse](
      "package/repository/list",
      PackageRepositoryListRequest(),
      MediaTypes.PackageRepositoryListRequest,
      MediaTypes.PackageRepositoryListResponse
    )
    response
  }

  private def searchPackages(req: SearchRequest): Response = {
    val request = CosmosClient.buildPost(
      "package/search",
      req,
      MediaTypes.SearchRequest,
      MediaTypes.SearchResponse
    )
    CosmosClient(request)
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
