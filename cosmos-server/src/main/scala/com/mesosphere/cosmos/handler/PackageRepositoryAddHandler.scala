package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.UniverseClientHttpError
import com.mesosphere.cosmos.error.UnsupportedRepositoryUri
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.repository.UniverseClient
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe.bijection.FutureConversions._
import com.twitter.util.Future
import io.netty.handler.codec.http.HttpResponseStatus

import scala.concurrent.ExecutionContext.Implicits.global

private[cosmos] final class PackageRepositoryAddHandler(
  sourcesStorage: PackageSourcesStorage,
  universeClient: UniverseClient,
  repositories: PackageCollection
) extends EndpointHandler[rpc.v1.model.PackageRepositoryAddRequest, rpc.v1.model.PackageRepositoryAddResponse] {

  override def apply(
    request: rpc.v1.model.PackageRepositoryAddRequest
  )(
    implicit session: RequestSession
  ): Future[rpc.v1.model.PackageRepositoryAddResponse] = {
    val repository = rpc.v1.model.PackageRepository(request.name, request.uri)
    request.uri.schemeOption match {
      case Some("http") | Some("https") =>
        checkThatRepoWorks(repository)
          .flatMap { _ =>
            sourcesStorage
              .add(request.index, repository)
              .map(rpc.v1.model.PackageRepositoryAddResponse(_))
              .foreach(_ => repositories.invalidateAll())
          }
      case _ => throw UnsupportedRepositoryUri(request.uri).exception
    }
  }

  private[this] def checkThatRepoWorks(
    repository: rpc.v1.model.PackageRepository
  )(
    implicit session: RequestSession,
  ): Future[Unit] = {
    /* We get the repo to see if the operation succeeds
     * We don't need the actual response
     */
    universeClient(repository).asTwitter.unit.handle {
      case ce: CosmosException =>
        ce.error match {
          case uce : UniverseClientHttpError =>
            throw ce.copy(error = uce.copy(status = HttpResponseStatus.BAD_REQUEST))
          case _ => throw ce
        }
    }
  }

}
