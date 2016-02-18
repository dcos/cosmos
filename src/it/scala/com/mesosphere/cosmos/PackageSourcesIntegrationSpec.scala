package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.IntegrationTests.withTempDirectory
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.{PackageSourceSpec, ZooKeeperStorage}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.{Http, Service}
import com.twitter.io.Buf
import com.twitter.util.Await
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.scalatest.FreeSpec
import org.scalatest.concurrent.Eventually

final class PackageSourcesIntegrationSpec
  extends FreeSpec with CosmosSpec with ZooKeeperFixture with Eventually {

  import PackageSourcesIntegrationSpec._

  "Package sources endpoints" in {
    withCosmosService { implicit cosmosService =>
      assertResult(List(PackageSourceSpec.UniverseRepository))(listSources(cosmosService))
      assertResult(
        PackageRepositoryAddResponse(
          List(
            PackageSourceSpec.SourceCliTest4,
            PackageSourceSpec.UniverseRepository
          )
        )
      )(
        addSource(cosmosService, PackageSourceSpec.SourceCliTest4)
      )

      assertListResult(List(PackageSourceSpec.SourceCliTest4, PackageSourceSpec.UniverseRepository))

      assertResult(PackageRepositoryDeleteResponse(List(PackageSourceSpec.SourceCliTest4))) {
        deleteSource(cosmosService, PackageSourceSpec.UniverseRepository)
      }

      assertListResult(List(PackageSourceSpec.SourceCliTest4))

      assertResult(PackageRepositoryDeleteResponse(Nil)) {
        deleteSource(cosmosService, PackageSourceSpec.SourceCliTest4)
      }

      assertListResult(Nil)
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

  private[this] def assertListResult(expected: List[PackageRepository])(
    implicit cosmosService: Service[Request, Response]
  ): Unit = {
    eventually {
      assertResult(expected)(listSources(cosmosService))
    }
  }

}

private object PackageSourcesIntegrationSpec extends CosmosSpec {

  import IntegrationTests.RepositoryMetadataOps

  private def listSources(cosmosService: Service[Request, Response]): Seq[PackageRepository] = {
    callEndpoint[PackageRepositoryListRequest, PackageRepositoryListResponse](
      cosmosService,
      "package/repository/list",
      PackageRepositoryListRequest(),
      MediaTypes.PackageRepositoryListRequest,
      MediaTypes.PackageRepositoryListResponse
    ).repositories.map(_.toDescriptor)
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

}
