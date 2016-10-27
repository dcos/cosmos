package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.repository.{DefaultRepositories, PackageRepositorySpec}
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.netaporter.uri.dsl._
import com.twitter.finagle.http._
import com.twitter.io.Buf
import io.circe.syntax._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{BeforeAndAfter, FreeSpec}

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
    val Xor.Right(response) = CosmosClient.callEndpoint[PackageRepositoryListRequest, PackageRepositoryListResponse](
      "package/repository/list",
      PackageRepositoryListRequest(),
      MediaTypes.PackageRepositoryListRequest,
      MediaTypes.PackageRepositoryListResponse
    )
    response.repositories
  }

  private def addRepository(
    source: PackageRepository
  ): PackageRepositoryAddResponse = {
    val Xor.Right(response) = CosmosClient.callEndpoint[PackageRepositoryAddRequest, PackageRepositoryAddResponse](
      "package/repository/add",
      PackageRepositoryAddRequest(source.name, source.uri),
      MediaTypes.PackageRepositoryAddRequest,
      MediaTypes.PackageRepositoryAddResponse
    )
    response
  }

  private def deleteRepository(
    source: PackageRepository
  ): PackageRepositoryDeleteResponse = {
    val Xor.Right(response) = CosmosClient.callEndpoint[PackageRepositoryDeleteRequest, PackageRepositoryDeleteResponse](
      "package/repository/delete",
      PackageRepositoryDeleteRequest(name = Some(source.name)),
      MediaTypes.PackageRepositoryDeleteRequest,
      MediaTypes.PackageRepositoryDeleteResponse
    )
    response
  }

  private def sendAddRequest(
    addRequest: PackageRepositoryAddRequest
  ): Response = {
    val path = "package/repository/add"
    val request = CosmosClient.requestBuilder(path)
      .addHeader("Content-type", MediaTypes.PackageRepositoryAddRequest.show)
      .addHeader("Accept", MediaTypes.PackageRepositoryAddResponse.show)
      .buildPost(Buf.Utf8(addRequest.asJson.noSpaces))

    CosmosClient(request)
  }

}
