package com.mesosphere.cosmos.handler

import cats.data.Ior
import com.mesosphere.cosmos.error.RepoNameOrUriMissing
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.rpc
import com.twitter.util.Future

private[cosmos] final class PackageRepositoryDeleteHandler(
  sourcesStorage: PackageSourcesStorage
) extends EndpointHandler[rpc.v1.model.PackageRepositoryDeleteRequest,
                          rpc.v1.model.PackageRepositoryDeleteResponse] {

  import PackageRepositoryDeleteHandler._

  override def apply(
    request: rpc.v1.model.PackageRepositoryDeleteRequest
  )(
    implicit session: RequestSession
  ): Future[rpc.v1.model.PackageRepositoryDeleteResponse] = {
    val nameOrUri = optionsToIor(request.name, request.uri).getOrElse(
      throw RepoNameOrUriMissing().exception
    )
    sourcesStorage.delete(nameOrUri).map { sources =>
      rpc.v1.model.PackageRepositoryDeleteResponse(sources)
    }
  }

}

object PackageRepositoryDeleteHandler {

  private def optionsToIor[A, B](aOpt: Option[A], bOpt: Option[B]): Option[Ior[A, B]] = {
    (aOpt, bOpt) match {
      case (Some(a), Some(b)) => Some(Ior.Both(a, b))
      case (Some(a), _) => Some(Ior.Left(a))
      case (_, Some(b)) => Some(Ior.Right(b))
      case _ => None
    }
  }

}
