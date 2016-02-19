package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.IntegrationTests.withTempDirectory
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.{PackageSourceSpec, ZooKeeperStorage}
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.{Http, Service}
import com.twitter.io.Buf
import com.twitter.util.{Future, Await}
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.scalatest.FreeSpec

final class PackageSourcesIntegrationSpec extends FreeSpec with CosmosSpec with ZooKeeperFixture {

  import PackageSourcesIntegrationSpec._

  "Package sources endpoints" in {
    withCosmosService { cosmosService =>
      assertResult(List(PackageSourceSpec.UniverseRepository))(listSources(cosmosService))
      assertResult(
        PackageRepositoryAddResponse(
          List(
            PackageSourceSpec.UniverseRepository,
            PackageSourceSpec.SourceCliTest4
          )
        )
      )(
        addSource(cosmosService, PackageSourceSpec.SourceCliTest4)
      )

      assertResult(List(PackageSourceSpec.UniverseRepository, PackageSourceSpec.SourceCliTest4)) {
        listSources(cosmosService)
      }

      assertResult(PackageRepositoryDeleteResponse(List(PackageSourceSpec.SourceCliTest4))) {
        deleteSource(cosmosService, PackageSourceSpec.UniverseRepository)
      }

      assertResult(List(PackageSourceSpec.SourceCliTest4))(listSources(cosmosService))

      assertResult(PackageRepositoryDeleteResponse(Nil)) {
        deleteSource(cosmosService, PackageSourceSpec.SourceCliTest4)
      }

      assertResult(Nil)(listSources(cosmosService))
    }
  }

  "Package repo add should" - {
    "enforce not adding outside list bounds" - {
      "-1" in {
        withCosmosService { cosmosService =>
          val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake", Some(-1))

          val response = Await.result(sendAddRequest(cosmosService, addRequest))
          assertResult(Status.BadRequest)(response.status)
        }
      }

      "2" in {
        withCosmosService { cosmosService =>
          val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake", Some(2))

          val response = Await.result(sendAddRequest(cosmosService, addRequest))
          assertResult(Status.BadRequest)(response.status)
        }
      }

      "10" in {
        withCosmosService { cosmosService =>
          val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake", Some(10))

          val response = Await.result(sendAddRequest(cosmosService, addRequest))
          assertResult(Status.BadRequest)(response.status)
        }
      }
    }

    "append to the list if no index defined" in {
      withCosmosService { cosmosService =>
        val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake")

        val response = Await.result(sendAddRequest(cosmosService, addRequest))
        assertResult(Status.Ok)(response.status)

        val sources = listSources(cosmosService)
        assertResult(PackageRepository(addRequest.name, addRequest.uri))(sources(1))
      }
    }

    "allows insertion at specific index" in {
      withCosmosService { cosmosService =>
        val addRequest = PackageRepositoryAddRequest("name", "http://fake.fake", Some(0))

        val response = Await.result(sendAddRequest(cosmosService, addRequest))
        assertResult(Status.Ok)(response.status)

        val sources = listSources(cosmosService)
        assertResult(PackageRepository(addRequest.name, addRequest.uri))(sources.head)
      }
    }
  }

  private[this] def withCosmosService(f: Service[Request, Response] => Unit): Unit = {
    withTempDirectory { universeDir =>
      val packageRunner = new MarathonPackageRunner(adminRouter)

      withZooKeeperClient { zkClient =>
        val sourcesStorage = new ZooKeeperStorage(zkClient, PackageSourceSpec.UniverseRepository.uri)

        val cosmos = Cosmos(adminRouter, packageRunner, sourcesStorage, universeDir)

        val service = cosmos.service
        val server = Http.serve(s":$servicePort", service)
        val client = Http.newService(s"127.0.0.1:$servicePort")

        try {
          f(service)
        } finally {
          Await.all(client.close(), server.close(), service.close())
        }
      }
    }
  }

}

private object PackageSourcesIntegrationSpec extends CosmosSpec {

  private def listSources(cosmosService: Service[Request, Response]): Seq[PackageRepository] = {
    callEndpoint[PackageRepositoryListRequest, PackageRepositoryListResponse](
      cosmosService,
      "package/repository/list",
      PackageRepositoryListRequest(),
      MediaTypes.PackageRepositoryListRequest,
      MediaTypes.PackageRepositoryListResponse
    ).repositories
  }

  private def addSource(
    cosmosService: Service[Request, Response],
    source: PackageRepository
  ): PackageRepositoryAddResponse = {
    callEndpoint[PackageRepositoryAddRequest, PackageRepositoryAddResponse](
      cosmosService,
      "package/repository/add",
      PackageRepositoryAddRequest(source.name, source.uri),
      MediaTypes.PackageRepositoryAddRequest,
      MediaTypes.PackageRepositoryAddResponse
    )
  }

  private def deleteSource(
    cosmosService: Service[Request, Response],
    source: PackageRepository
  ): PackageRepositoryDeleteResponse = {
    callEndpoint[PackageRepositoryDeleteRequest, PackageRepositoryDeleteResponse](
      cosmosService,
      "package/repository/delete",
      PackageRepositoryDeleteRequest(name = Some(source.name)),
      MediaTypes.PackageRepositoryDeleteRequest,
      MediaTypes.PackageRepositoryDeleteResponse
    )
  }

  private[this] def callEndpoint[Req, Res](
    cosmosService: Service[Request, Response],
    path: String,
    requestBody: Req,
    requestMediaType: MediaType,
    responseMediaType: MediaType
  )(implicit decoder: Decoder[Res], encoder: Encoder[Req]): Res = {
    val request = requestBuilder(path)
      .addHeader("Content-type", requestMediaType.show)
      .addHeader("Accept", responseMediaType.show)
        .buildPost(Buf.Utf8(requestBody.asJson.noSpaces))

    val response = Await.result(cosmosService(request))
    logger.debug("response to endpoint {}: {}", Seq(path, response.contentString): _*)

    assertResult(Status.Ok)(response.status)
    val Xor.Right(decodedBody) = decode[Res](response.contentString)
    decodedBody
  }


  private def sendAddRequest(
    cosmosService: Service[Request, Response],
    addRequest: PackageRepositoryAddRequest
  ): Future[Response] = {
    val path = "package/repository/add"
    val request = requestBuilder(path)
      .addHeader("Content-type", MediaTypes.PackageRepositoryAddRequest.show)
      .addHeader("Accept", MediaTypes.PackageRepositoryAddResponse.show)
      .buildPost(Buf.Utf8(addRequest.asJson.noSpaces))

    cosmosService(request).onSuccess { response =>
      logger.debug("response to endpoint {}: {}", Seq(path, response.contentString): _*)
    }
  }
}
