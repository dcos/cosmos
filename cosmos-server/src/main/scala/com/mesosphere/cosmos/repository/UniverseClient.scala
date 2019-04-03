package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.BuildProperties
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
import com.mesosphere.http.CompoundMediaType
import com.mesosphere.http.MediaType
import com.mesosphere.universe
import com.mesosphere.universe.MediaTypes
import com.twitter.finagle.http.Fields
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.Stat
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw
import io.netty.handler.codec.http.HttpResponseStatus
import java.io.InputStream
import org.jboss.netty.handler.codec.http.HttpMethod
import scala.io.Codec
import scala.io.Source

trait UniverseClient {
  def apply(
    repository: rpc.v1.model.PackageRepository
  )(
    implicit session: RequestSession
  ): Future[universe.v4.model.Repository]
}

final class DefaultUniverseClient(
  adminRouter: AdminRouter
)(
  implicit statsReceiver: StatsReceiver = NullStatsReceiver
) extends UniverseClient {

  import DefaultUniverseClient._

  private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  private[this] val stats = statsReceiver.scope("repositoryFetcher")
  private[this] val fetchScope = stats.scope("fetch")

  private[this] val cosmosVersion = BuildProperties().cosmosVersion

  def apply(
    repository: rpc.v1.model.PackageRepository
  )(
    implicit session: RequestSession
  ): Future[universe.v4.model.Repository] = {
    adminRouter.getDcosVersion().flatMap { dcosVersion =>
      apply(repository, dcosVersion.version).respond {
        case Return(_) =>
          logger.info(
            s"Success while fetching Universe state from ($repository, ${dcosVersion.version})"
          )
        case Throw(error) =>
          logger.error(
            s"Error while fetching Universe state from ($repository, ${dcosVersion.version})",
            error
          )
      }
    }
  }

  private[repository] def apply(
    repository: rpc.v1.model.PackageRepository,
    dcosReleaseVersion: universe.v3.model.DcosReleaseVersion
  ): Future[universe.v4.model.Repository] = {
    fetchScope.counter("requestCount").incr()
    Stat.timeFuture(fetchScope.stat("histogram")) {
      val acceptedMediaTypes = CompoundMediaType(
        MediaTypes.UniverseV5Repository,
        MediaTypes.UniverseV4Repository,
        MediaTypes.UniverseV3Repository
      )

      HttpClient
        .fetch(
          repository.uri,
          Fields.Accept -> acceptedMediaTypes.show,
          Fields.AcceptEncoding -> "gzip",
          Fields.UserAgent -> s"cosmos/$cosmosVersion dcos/${dcosReleaseVersion.show}"
        ) { responseData =>
          decodeAndSortUniverse(
            responseData.contentType,
            responseData.contentStream
          )
        }(fetchScope)
        .handle { case cosmosException: CosmosException =>
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
        }
    }
  }

  private[this] def decodeAndSortUniverse(
    contentType: MediaType,
    bodyInputStream: InputStream
  ): universe.v4.model.Repository = {

    def processAsV4Repository(version : String) : universe.v4.model.Repository = {
      val scope = fetchScope.scope("decode").scope(version)
      scope.counter("count").incr()
      Stat.time(scope.stat("histogram")) {
        decode[universe.v4.model.Repository](
          Source.fromInputStream(bodyInputStream, Codec.UTF8.toString).mkString
        ).getOrThrow
      }
    }

    // Decode the packages
    val repo = if (contentType.isCompatibleWith(MediaTypes.UniverseV5Repository)) {
      processAsV4Repository("v5")
    } else if (contentType.isCompatibleWith(MediaTypes.UniverseV4Repository)) {
      processAsV4Repository("v4")
    } else if (contentType.isCompatibleWith(MediaTypes.UniverseV3Repository)) {
      processAsV4Repository("v3")
    } else {
      throw UnsupportedContentType.forMediaType(SupportedMediaTypes, Some(contentType)).exception
    }

    // Sort the packages
    universe.v4.model.Repository(repo.packages.sorted.reverse)

  }
}

object DefaultUniverseClient {

  val TemporaryRedirect: Int = 307
  val PermanentRedirect: Int = 308

  val SupportedMediaTypes: List[MediaType] =
    List(MediaTypes.UniverseV4Repository, MediaTypes.UniverseV3Repository, MediaTypes.UniverseV2Repository)
}

object UniverseClient {
  def apply(adminRouter: AdminRouter)(implicit statsReceiver: StatsReceiver = NullStatsReceiver): UniverseClient = {
    new DefaultUniverseClient(adminRouter)
  }
}
