package com.mesosphere.cosmos.repository

import com.google.common.io.CharStreams
import com.mesosphere.cosmos.rpc
import com.twitter.util.Try
import io.circe.jawn.decode
import java.io.InputStreamReader
import scala.util.Either
import scala.util.Left
import scala.util.Right

private[repository] class DefaultRepositories private[repository](resourceName: String) {
  private val repos: Try[Either[io.circe.Error, List[rpc.v1.model.PackageRepository]]] = Try {
    Option(this.getClass.getResourceAsStream(resourceName)) match {
      case Some(is) =>
        val json = CharStreams.toString(new InputStreamReader(is))
        decode[List[rpc.v1.model.PackageRepository]](json)
      case _ =>
        throw new IllegalStateException(s"Unable to load classpath resource: $resourceName")
    }
  }
}

object DefaultRepositories {
  private[this] val loaded = new DefaultRepositories("/default-repositories.json")

  def apply(): DefaultRepositories = loaded

  implicit class DefaultRepositoriesOps(val dr: DefaultRepositories) extends AnyVal {
    def get(): Try[Either[io.circe.Error, List[rpc.v1.model.PackageRepository]]] = {
      dr.repos
    }

    def getOrThrow: List[rpc.v1.model.PackageRepository] = {
      get().map {
        case Right(list) => list
        case Left(err) => throw err
      }.get
    }

    def getOrElse(
      orElse: List[rpc.v1.model.PackageRepository]
    ): List[rpc.v1.model.PackageRepository] = {
      get().map(_.getOrElse(orElse)).getOrElse(orElse)
    }
  }

}
