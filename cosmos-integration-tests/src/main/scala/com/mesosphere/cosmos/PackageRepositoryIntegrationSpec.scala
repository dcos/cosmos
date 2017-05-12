package com.mesosphere.cosmos

import com.mesosphere.cosmos.ItUtil._
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.repository.DefaultRepositories
import com.mesosphere.cosmos.repository.PackageRepositorySpec
import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.netaporter.uri.dsl._
import com.twitter.finagle.http._
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FreeSpec
import org.scalatest.prop.TableDrivenPropertyChecks

final class PackageRepositoryIntegrationSpec extends FreeSpec with BeforeAndAfter with BeforeAndAfterAll {

  import PackageRepositoryIntegrationSpec._

  private val defaultRepos = DefaultRepositories().getOrThrow
  var originalRepositories: Seq[PackageRepository] = Seq.empty

  override def beforeAll(): Unit = {
    super.beforeAll()
    originalRepositories = listRepositories()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    replaceRepositoriesWith(originalRepositories)
  }

  before {
    replaceRepositoriesWith(defaultRepos)
  }

  "Package repository endpoints" in {
    // assert repos are default list
    val list1 = listRepositories()
    assertResult(defaultRepos)(list1)

    // add SourceCliTest4 to repo list
    assertResult(
      PackageRepositoryAddResponse(
        defaultRepos :+ PackageRepositorySpec.SourceCliTest4
      )
    )(
      addRepository(PackageRepositorySpec.SourceCliTest4)
    )

    // assert repos are default + SourceCliTest4
    assertResult(defaultRepos :+ PackageRepositorySpec.SourceCliTest4) {
      listRepositories()
    }

    // delete SourceCliTest4
    assertResult(PackageRepositoryDeleteResponse(defaultRepos)) {
      deleteRepository(PackageRepositorySpec.SourceCliTest4)
    }

    // assert repos are default list
    assertResult(defaultRepos)(listRepositories())
  }

  "Package repo add should" - {
    "enforce not adding outside list bounds" - {
      "-1" in {
          val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake", Some(-1))

          val response = sendAddRequest(addRequest)
          assertResult(Status.BadRequest)(response.status)
      }

      "2" in {
          val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake", Some(defaultRepos.size + 1))

          val response = sendAddRequest(addRequest)
          assertResult(Status.BadRequest)(response.status)
      }

      "10" in {
        val index = 10
        val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake", Some(index))

        val response = sendAddRequest(addRequest)
        assertResult(Status.BadRequest)(response.status)
      }
    }

    "append to the list if no index defined" in {
        val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake")

        val response = sendAddRequest(addRequest)
        assertResult(Status.Ok)(response.status)

        val sources = listRepositories()
        assertResult(PackageRepository(addRequest.name, addRequest.uri))(sources(defaultRepos.size))
    }

    "allows insertion at specific index" in {
        val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake", Some(0))

        val response = sendAddRequest(addRequest)
        assertResult(Status.Ok)(response.status)

        val sources = listRepositories()
        assertResult(PackageRepository(addRequest.name, addRequest.uri))(sources.head)
    }
  }
}

private object PackageRepositoryIntegrationSpec extends TableDrivenPropertyChecks {

  private def sendAddRequest(
    addRequest: PackageRepositoryAddRequest
  ): Response = {
    val request = CosmosRequests.packageRepositoryAdd(addRequest)
    CosmosClient.submit(request)
  }

}
