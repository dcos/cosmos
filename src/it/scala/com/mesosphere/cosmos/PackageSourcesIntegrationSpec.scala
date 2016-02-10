package com.mesosphere.cosmos

import cats.data.Xor
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

final class PackageSourcesIntegrationSpec extends FreeSpec with CosmosSpec with ZooKeeperFixture {

  import PackageSourcesIntegrationSpec._

  "Package sources endpoints" in {
    withCosmosService { cosmosService =>
      assertResult(List(UniverseSource))(listSources(cosmosService))
      assertResult(
        PackageRepositoryAddResponse(
          List(
            PackageSourceSpec.SourceCliTest4,
            UniverseSource
          )
        )
      )(
        addSource(cosmosService, PackageSourceSpec.SourceCliTest4)
      )

      assertResult(List(PackageSourceSpec.SourceCliTest4, UniverseSource)) {
        listSources(cosmosService)
      }

      assertResult(PackageRepositoryDeleteResponse(List(PackageSourceSpec.SourceCliTest4))) {
        deleteSource(cosmosService, UniverseSource)
      }

      assertResult(List(PackageSourceSpec.SourceCliTest4))(listSources(cosmosService))

      assertResult(PackageRepositoryDeleteResponse(Nil)) {
        deleteSource(cosmosService, PackageSourceSpec.SourceCliTest4)
      }

      assertResult(Nil)(listSources(cosmosService))
    }
  }

  private[this] def withCosmosService(f: Service[Request, Response] => Unit): Unit = {
    withTempDirectory { universeDir =>
      val packageCache = UniversePackageCache(universeUri, universeDir)
      val packageRunner = new MarathonPackageRunner(adminRouter)

      withZooKeeperClient { zkClient =>
        val sourcesStorage = new ZooKeeperStorage(zkClient, universeUri)

        val cosmos = Cosmos(adminRouter, packageCache, packageRunner, sourcesStorage)

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

  private val UniverseSource = PackageRepository("Universe", universeUri)

  private def listSources(cosmosService: Service[Request, Response]): Seq[PackageRepository] = {
    callEndpoint[PackageRepositoryListRequest, PackageRepositoryListResponse](
      cosmosService,
      "package/repository/list",
      PackageRepositoryListRequest(),
      MediaTypes.PackageRepositoryListRequest,
      MediaTypes.PackageRepositoryListResponse
    ).sources
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
    assertResult(Status.Ok)(response.status)
    val Xor.Right(decodedBody) = decode[Res](response.contentString)
    decodedBody
  }

}
