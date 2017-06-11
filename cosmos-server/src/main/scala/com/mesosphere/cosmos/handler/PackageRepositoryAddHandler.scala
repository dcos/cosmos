package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.error.UnsupportedRepositoryUri
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.rpc
import com.twitter.util.Future

private[cosmos] final class PackageRepositoryAddHandler(
  sourcesStorage: PackageSourcesStorage
) extends EndpointHandler[rpc.v1.model.PackageRepositoryAddRequest,
                          rpc.v1.model.PackageRepositoryAddResponse] {

  override def apply(
    request: rpc.v1.model.PackageRepositoryAddRequest
  )(
    implicit session: RequestSession
  ): Future[rpc.v1.model.PackageRepositoryAddResponse] = {
    request.uri.scheme match {
      case Some("http") | Some("https") =>
        sourcesStorage.add(
          request.index,
          rpc.v1.model.PackageRepository(request.name, request.uri)
        ) map { sources =>
          rpc.v1.model.PackageRepositoryAddResponse(sources)
        }
      case _ => throw UnsupportedRepositoryUri(request.uri).exception
    }
  }

}
