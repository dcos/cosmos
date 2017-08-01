package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.common.util.Clock
import com.twitter.util.Future
import java.util.concurrent.TimeUnit
import scala.collection.breakOut

final class RepositoryCache(
  packageRepositoryStorage: PackageSourcesStorage,
  universeClient: UniverseClient,
  clock: Clock = Clock.SYSTEM_CLOCK
){

  @volatile private[this] var cachedRepos =
    Map.empty[Uri, (universe.v4.model.Repository, Long)]

  def all()(
    implicit session: RequestSession
  ): Future[List[(universe.v4.model.Repository, Uri)]] = {
    packageRepositoryStorage.readCache().flatMap { packageRepositories =>
      val oldCachedRepos = cachedRepos
      update(oldCachedRepos, packageRepositories).onSuccess { newRepositories =>
        cachedRepos = newRepositories.toMap
      } map { newRepositories =>
        val result: List[(universe.v4.model.Repository, Uri)] = newRepositories.map {
          case (uri, (repo, _)) => (repo, uri)
        }(breakOut)
        result
      }
    }
  }

  private[this] def update(
    oldMap: Map[Uri, (universe.v4.model.Repository, Long)],
    packageRepositories: List[rpc.v1.model.PackageRepository]
  )(
    implicit session: RequestSession
  ): Future[Seq[(Uri, (universe.v4.model.Repository, Long))]] = {
    Future.collect(
      packageRepositories.map { packageRepository =>
        oldMap.get(packageRepository.uri) match {
          case Some((repo, timeStamp)) =>
            fetch(packageRepository, timeStamp, repo).map(value => (packageRepository.uri, value))
          case None =>
            fetch(packageRepository).map(value => (packageRepository.uri, value))
        }
      }
    )
  }

  private[this] def fetch(
    packageRepository: rpc.v1.model.PackageRepository,
    lastTimeStamp: Long,
    oldRepository: universe.v4.model.Repository
  )(
    implicit session: RequestSession
  ): Future[(universe.v4.model.Repository, Long)] = {
    val now = TimeUnit.MILLISECONDS.toSeconds(clock.nowMillis())
    val lastSec = TimeUnit.MILLISECONDS.toSeconds(lastTimeStamp)
    val refetchAt = lastSec + TimeUnit.MINUTES.toSeconds(1)
    if(refetchAt < now) {
      fetch(packageRepository)
    } else {
      Future((oldRepository, lastTimeStamp))
    }
  }

  private[this] def fetch(
    packageRepository: rpc.v1.model.PackageRepository
  )(
    implicit session: RequestSession
  ): Future[(universe.v4.model.Repository, Long)] = {
    universeClient(packageRepository).map { newRepository =>
      (newRepository, clock.nowMillis())
    }
  }
}
