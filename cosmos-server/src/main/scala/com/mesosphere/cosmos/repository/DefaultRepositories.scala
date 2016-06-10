package com.mesosphere.cosmos.repository

import java.io.InputStreamReader

import cats.data.Xor
import com.google.common.io.CharStreams
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.twitter.util.Try
import io.circe.parse._

private[repository] class DefaultRepositories private[repository](resourceName: String) {
  private val repos: Try[Xor[io.circe.Error, List[PackageRepository]]] = Try {
    val is = this.getClass.getResourceAsStream(resourceName)
    if (is == null) {
      throw new IllegalStateException(s"Unable to load classpath resource: $resourceName")
    }
    val json = CharStreams.toString(new InputStreamReader(is))

    decode[List[PackageRepository]](json)
  }
}

object DefaultRepositories {
  private[this] val loaded = new DefaultRepositories("/default-repositories.json")

  def apply() = loaded

  implicit class DefaultRepositoriesOps(val dr: DefaultRepositories) extends AnyVal {
    def get(): Try[Xor[io.circe.Error, List[PackageRepository]]] = {
      dr.repos
    }

    def getOrThrow: List[PackageRepository] = {
      get().map {
        case Xor.Right(list) => list
        case Xor.Left(err) => throw err
      }.get
    }

    def getOrElse(orElse: List[PackageRepository]): List[PackageRepository] = {
      get().map(_.getOrElse(orElse)).getOrElse(orElse)
    }
  }

}
