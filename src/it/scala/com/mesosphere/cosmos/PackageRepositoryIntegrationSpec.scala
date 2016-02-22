package com.mesosphere.cosmos

import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.PackageRepositorySpec
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.http._
import com.twitter.io.Buf
import io.circe.syntax._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{BeforeAndAfter, FreeSpec}

final class PackageRepositoryIntegrationSpec extends FreeSpec with BeforeAndAfter {

  import PackageRepositoryIntegrationSpec._

  var originalRepositories: Seq[PackageRepository] = null

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
    assertResult(List(UniverseSource))(listRepositories())
    assertResult(
      PackageRepositoryAddResponse(
        List(
          UniverseSource,
          PackageRepositorySpec.SourceCliTest4
        )
      )
    )(
      addRepository(PackageRepositorySpec.SourceCliTest4)
    )

    assertResult(List(UniverseSource, PackageRepositorySpec.SourceCliTest4)) {
      listRepositories()
    }

    assertResult(PackageRepositoryDeleteResponse(List(PackageRepositorySpec.SourceCliTest4))) {
      deleteRepository(UniverseSource)
    }

    assertResult(List(PackageRepositorySpec.SourceCliTest4))(listRepositories())

    assertResult(PackageRepositoryDeleteResponse(Nil)) {
      deleteRepository(PackageRepositorySpec.SourceCliTest4)
    }

    assertResult(Nil)(listRepositories())
  }

  "Package repo add should" - {
    "enforce not adding outside list bounds" - {
      "-1" in {
          val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake", Some(-1))

          val response = sendAddRequest(addRequest)
          assertResult(Status.BadRequest)(response.status)
      }

      "2" in {
          val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake", Some(2))

          val response = sendAddRequest(addRequest)
          assertResult(Status.BadRequest)(response.status)
      }

      "10" in {
          val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake", Some(10))

          val response = sendAddRequest(addRequest)
          assertResult(Status.BadRequest)(response.status)
      }
    }

    "append to the list if no index defined" in {
        val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake")

        val response = sendAddRequest(addRequest)
        assertResult(Status.Ok)(response.status)

        val sources = listRepositories()
        assertResult(PackageRepository(addRequest.name, addRequest.uri))(sources(1))
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

  protected[this] val universeUri: Uri =
    Uri.parse(System.getProperty("com.mesosphere.cosmos.universeBundleUri"))
  private val UniverseSource = PackageRepository("Universe", universeUri)

  private def listRepositories(): Seq[PackageRepository] = {
    CosmosClient.callEndpoint[PackageRepositoryListRequest, PackageRepositoryListResponse](
      "package/repository/list",
      PackageRepositoryListRequest(),
      MediaTypes.PackageRepositoryListRequest,
      MediaTypes.PackageRepositoryListResponse
    ).repositories
  }

  private def addRepository(
    source: PackageRepository
  ): PackageRepositoryAddResponse = {
    CosmosClient.callEndpoint[PackageRepositoryAddRequest, PackageRepositoryAddResponse](
      "package/repository/add",
      PackageRepositoryAddRequest(source.name, source.uri),
      MediaTypes.PackageRepositoryAddRequest,
      MediaTypes.PackageRepositoryAddResponse
    )
  }

  private def deleteRepository(
    source: PackageRepository
  ): PackageRepositoryDeleteResponse = {
    CosmosClient.callEndpoint[PackageRepositoryDeleteRequest, PackageRepositoryDeleteResponse](
      "package/repository/delete",
      PackageRepositoryDeleteRequest(name = Some(source.name)),
      MediaTypes.PackageRepositoryDeleteRequest,
      MediaTypes.PackageRepositoryDeleteResponse
    )
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
