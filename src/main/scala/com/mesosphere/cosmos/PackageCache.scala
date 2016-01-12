package com.mesosphere.cosmos

import cats.data.Xor
import com.twitter.util.Future
import io.circe.Json

/** A repository of packages that can be installed on DCOS. */
trait PackageCache {

  def getMarathonJson(packageName: String, version: Option[String]): Future[CosmosResult[Json]]

}

object PackageCache {

  /** Useful when a cache is not needed or should not be used. */
  object empty extends PackageCache {
    def getMarathonJson(packageName: String, version: Option[String]): Future[CosmosResult[Json]] =
      Future.value(Xor.Left(errorNel(PackageNotFound(packageName))))
  }

}
