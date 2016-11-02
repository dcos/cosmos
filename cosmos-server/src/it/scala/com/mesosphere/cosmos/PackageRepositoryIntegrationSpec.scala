package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.repository.DefaultRepositories
import com.mesosphere.cosmos.repository.PackageRepositorySpec
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.test.CosmosRequest
import com.netaporter.uri.dsl._
import com.twitter.finagle.http._
import org.scalatest.BeforeAndAfter
import org.scalatest.FreeSpec
import org.scalatest.prop.TableDrivenPropertyChecks

final class PackageRepositoryIntegrationSpec extends FreeSpec with BeforeAndAfter {

  import PackageRepositoryIntegrationSpec._

  private val defaultRepos = DefaultRepositories().getOrThrow
  var originalRepositories: Seq[PackageRepository] = Seq.empty

  before {
    originalRepositories = listRepositories()
  }

  after {
    listRepositories().foreach { repo =>
      deleteRepository(repo)
    }
    originalRepositories.foreach { repo =>
      addRepository(repo)
    }
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

  private def listRepositories(): Seq[PackageRepository] = {
    val request = CosmosRequest.post(
      "package/repository/list",
      PackageRepositoryListRequest(),
      MediaTypes.PackageRepositoryListRequest,
      MediaTypes.PackageRepositoryListResponse
    )
    val Xor.Right(response) = CosmosClient.callEndpoint[PackageRepositoryListResponse](request)
    response.repositories
  }

  private def addRepository(
    source: PackageRepository
  ): PackageRepositoryAddResponse = {
    val request = CosmosRequest.post(
      "package/repository/add",
      PackageRepositoryAddRequest(source.name, source.uri),
      MediaTypes.PackageRepositoryAddRequest,
      MediaTypes.PackageRepositoryAddResponse
    )
    val Xor.Right(response) = CosmosClient.callEndpoint[PackageRepositoryAddResponse](request)
    response
  }

  private def deleteRepository(
    source: PackageRepository
  ): PackageRepositoryDeleteResponse = {
    val request = CosmosRequest.post(
      "package/repository/delete",
      PackageRepositoryDeleteRequest(name = Some(source.name)),
      MediaTypes.PackageRepositoryDeleteRequest,
      MediaTypes.PackageRepositoryDeleteResponse
    )
    val Xor.Right(response) = CosmosClient.callEndpoint[PackageRepositoryDeleteResponse](request)
    response
  }

  private def sendAddRequest(
    addRequest: PackageRepositoryAddRequest
  ): Response = {
    val request = CosmosRequest.post(
      path = "package/repository/add",
      body = addRequest,
      contentType = MediaTypes.PackageRepositoryAddRequest,
      accept = MediaTypes.PackageRepositoryAddResponse
    )
    CosmosClient.submit(request)
  }

}
