package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.HttpClient
import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.EndpointUriConnection
import com.mesosphere.cosmos.error.EndpointUriSyntax
import com.mesosphere.cosmos.error.GenericHttpError
import com.mesosphere.cosmos.error.RepositoryUriConnection
import com.mesosphere.cosmos.error.RepositoryUriSyntax
import com.mesosphere.cosmos.error.UniverseClientHttpError
import com.mesosphere.cosmos.error.UnsupportedContentType
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc
import com.mesosphere.error.ResultOps
import com.mesosphere.http.MediaType
import com.mesosphere.universe
import com.mesosphere.universe.MediaTypes
import com.mesosphere.universe.bijection.FutureConversions._
import com.mesosphere.universe.bijection.MediaTypeConversions._
import com.mesosphere.usi.async.ExecutionContexts
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.Stat
import com.twitter.finagle.stats.StatsReceiver
import io.netty.handler.codec.http.HttpResponseStatus
import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpResponse, MediaRange}
import akka.http.scaladsl.model.headers.{Accept, HttpEncodings, ProductVersion, `Accept-Encoding`, `User-Agent`}
import akka.stream.Materializer
import akka.stream.alpakka.json.scaladsl.JsonReader
import akka.stream.scaladsl.Sink
import org.jboss.netty.handler.codec.http.HttpMethod

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait UniverseClient {
  def apply(
    repository: rpc.v1.model.PackageRepository
  )(
    implicit session: RequestSession,
    ec: ExecutionContext
  ): Future[universe.v4.model.Repository]
}

final class DefaultUniverseClient(
  adminRouter: AdminRouter
)(
  implicit statsReceiver: StatsReceiver = NullStatsReceiver,
  system: ActorSystem
) extends UniverseClient {

  import DefaultUniverseClient._

  private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  private[this] val stats = statsReceiver.scope("repositoryFetcher")
  private[this] val fetchScope = stats.scope("fetch")

  override def apply(
    repository: rpc.v1.model.PackageRepository
  )(
    implicit session: RequestSession,
    ec: ExecutionContext
  ): Future[universe.v4.model.Repository] = {
    adminRouter.getDcosVersion().flatMap { dcosVersion =>
      val result = apply(repository, dcosVersion.version)
     result.onComplete{
        case Success(_) =>
          logger.info(
            s"Success while fetching Universe state from ($repository, ${dcosVersion.version})"
          )
        case Failure(error) =>
          logger.error(
            s"Error while fetching Universe state from ($repository, ${dcosVersion.version})",
            error
          )
      }
      result.asTwitter
    }.asScala
  }

  private[repository] def apply(
    repository: rpc.v1.model.PackageRepository,
    dcosReleaseVersion: universe.v3.model.DcosReleaseVersion
  )(
    implicit ec: ExecutionContext,
    mat: Materializer
  ): Future[universe.v4.model.Repository] = {
    fetchScope.counter("requestCount").incr()
    Stat.timeFuture(fetchScope.stat("histogram")) {
      val acceptedMediaTypes: Vector[MediaRange]= Vector(
        MediaTypes.UniverseV5Repository,
        MediaTypes.UniverseV4Repository,
        MediaTypes.UniverseV3Repository
      )

      HttpClient
        .fetch(
          repository.uri,
          new Accept(acceptedMediaTypes),
          `Accept-Encoding`(HttpEncodings.gzip),
          `User-Agent`(ProductVersion("dcos", dcosReleaseVersion.show))
        ) { response=>
          decodeAndSortUniverse(response)
        }(fetchScope, ec, system)
        .recover {
          case cosmosException: CosmosException =>
          cosmosException.error match {
            case EndpointUriSyntax(_, message) =>
              throw cosmosException.copy(error = RepositoryUriSyntax(repository, message))
            case EndpointUriConnection(_, message) =>
              throw cosmosException.copy(error = RepositoryUriConnection(repository, message))
            case GenericHttpError(_, _, clientStatus, _) =>
              /* If we are unable to get the latest Universe we should not forward the status code
               * returned. We should instead return 500 to the client and include the actual error
               * in the message.
               */
              throw UniverseClientHttpError(
                repository,
                HttpMethod.GET,
                clientStatus,
                HttpResponseStatus.INTERNAL_SERVER_ERROR
              ).exception
            case e => throw e.exception
          }
        }.asTwitter
    }
  }.asScala

  private[this] def decodeAndSortUniverse(response: HttpResponse)(
    implicit ex: ExecutionContext,
    mat: Materializer
  ): Future[universe.v4.model.Repository] = async {

    def processAsV4Repository(version : String) : Future[universe.v4.model.Repository] = {
      val scope = fetchScope.scope("decode").scope(version)
      scope.counter("count").incr()
      Stat.timeFuture(scope.stat("histogram")) {
        response.entity.dataBytes
          .via(JsonReader.select("$.packages[*]"))
          .map { chunk =>
            decode[universe.v4.model.PackageDefinition](chunk.decodeString(StandardCharsets.UTF_8))
              .getOrThrow
          }
          .runWith(Sink.seq)
          .map(packages => universe.v4.model.Repository(packages.toVector))(ExecutionContexts.callerThread)
          .asTwitter
      }.asScala
    }

    // Decode the packages
    logger.info(s"# Headers: ${response.headers.mkString(", ")}")
    val mediaType = response.entity.contentType.mediaType.asCosmos
    val repo =if (mediaType.isCompatibleWith(MediaTypes.UniverseV5Repository.asCosmos)) {
      await(processAsV4Repository("v5"))
    } else if (mediaType.isCompatibleWith(MediaTypes.UniverseV4Repository.asCosmos)) {
      await(processAsV4Repository("v4"))
    } else if (mediaType.isCompatibleWith(MediaTypes.UniverseV3Repository.asCosmos)) {
      await(processAsV4Repository("v3"))
    } else {
      throw UnsupportedContentType.forMediaType(SupportedMediaTypes, Some(mediaType)).exception
    }

    // Sort the packages
    universe.v4.model.Repository(repo.packages.sorted.reverse)

  }
}

object DefaultUniverseClient {

  val TemporaryRedirect: Int = 307
  val PermanentRedirect: Int = 308

  val SupportedMediaTypes: List[MediaType] = List(
    MediaTypes.UniverseV4Repository.asCosmos, MediaTypes.UniverseV3Repository.asCosmos
  )
}

object UniverseClient {
  def apply(adminRouter: AdminRouter)(
    implicit statsReceiver: StatsReceiver = NullStatsReceiver,
    system: ActorSystem
  ): UniverseClient = {
    new DefaultUniverseClient(adminRouter)
  }
}
