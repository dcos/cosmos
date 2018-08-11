package com.mesosphere.cosmos.repository

import com.github.benmanes.caffeine.cache.Caffeine
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.cache.caffeine.LoadingFutureCache
import com.twitter.util.Future
import java.util.concurrent.TimeUnit

final class RepositoryCache(
  packageRepositoryStorage: PackageSourcesStorage,
  universeClient: UniverseClient
) {

  lazy val futureCache: LoadingFutureCache[(rpc.v1.model.PackageRepository,
    RequestSession), (universe.v4.model.Repository, Uri)] =
    new LoadingFutureCache(
      Caffeine
        .newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build { case (
          packageRepository: rpc.v1.model.PackageRepository,
          session: RequestSession
          ) => universeClient(packageRepository)(session).map((_,packageRepository.uri))
        }
    )

  def all()(
    implicit session: RequestSession
  ): Future[List[(universe.v4.model.Repository, Uri)]] = packageRepositoryStorage
    .readCache()
    .flatMap(
      Future.traverseSequentially(_)(
        pkgRepo => futureCache((pkgRepo, session))
      ).map(_.toList)
    )
}
