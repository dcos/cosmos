package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.universe
import com.mesosphere.universe.v4.model.Repository
import com.netaporter.uri.Uri
import com.twitter.common.util.Clock
import com.twitter.util.Future
import java.util.concurrent.TimeUnit

class RepositoryCache(
  packageRepositoryStorage: PackageSourcesStorage,
  universeClient: UniverseClient,
  clock: Clock = Clock.SYSTEM_CLOCK
){

  @volatile private[this] var cachedRepos =
    Map.empty[Uri, (universe.v4.model.Repository, Long)]

  def all()(
    implicit session: RequestSession
  ): Future[List[(Uri, Repository)]] = {
    packageRepositoryStorage.readCache().flatMap { packageRepositories =>
      val oldCachedRepos = cachedRepos
      update(oldCachedRepos, packageRepositories).onSuccess { newRepositories =>
        cachedRepos = newRepositories
      } map { newRepositories =>
        newRepositories.map { case (uri, (repo, _)) =>
          (uri, repo)
        }.toList
      }
    }
  }

  private[this] def update(
    oldMap: Map[Uri, (universe.v4.model.Repository, Long)],
    packageRepositories: List[PackageRepository]
  )(
    implicit session: RequestSession
  ): Future[Map[Uri, (universe.v4.model.Repository, Long)]] = {
    Future.traverseSequentially(packageRepositories) { packageRepository =>
      oldMap.get(packageRepository.uri) match {
        case Some((repo, timeStamp)) =>
          fetch(packageRepository, timeStamp, repo).map(value => (packageRepository.uri, value))
        case None =>
          fetch(packageRepository).map(value => (packageRepository.uri, value))
      }
    } map(_.toMap)
  }

  private[this] def fetch(
    packageRepository: PackageRepository,
    lastTimeStamp: Long,
    oldRepository: universe.v4.model.Repository
  )(
    implicit session: RequestSession
  ): Future[(Repository, Long)] = {
    val now = TimeUnit.MILLISECONDS.toSeconds(clock.nowMillis())
    val lastSec = TimeUnit.MILLISECONDS.toSeconds(lastTimeStamp)
    val refetchAt = lastSec + TimeUnit.MINUTES.toSeconds(1)
    if(refetchAt < now || lastSec > now) {
      universeClient(packageRepository).map { newRepository =>
        (newRepository, clock.nowMillis())
      }
    } else {
      Future((oldRepository, lastTimeStamp))
    }
  }

  private[this] def fetch(packageRepository: PackageRepository)(
    implicit session: RequestSession
  ): Future[(Repository, Long)] = {
    universeClient(packageRepository).map { newRepository =>
      (newRepository, clock.nowMillis())
    }
  }
}
