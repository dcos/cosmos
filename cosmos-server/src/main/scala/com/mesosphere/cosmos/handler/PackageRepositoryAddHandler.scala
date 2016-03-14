package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.UnsupportedRepositoryUri
import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import com.mesosphere.cosmos.model.PackageRepositoryAddRequest
import com.mesosphere.cosmos.model.PackageRepositoryAddResponse
import com.mesosphere.cosmos.model.PackageRepository
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

final class PackageRepositoryAddHandler(sourcesStorage: PackageSourcesStorage)(
  implicit
  decoder: DecodeRequest[PackageRepositoryAddRequest],
  encoder: Encoder[PackageRepositoryAddResponse]
) extends EndpointHandler[PackageRepositoryAddRequest, PackageRepositoryAddResponse] {

  override val accepts: MediaType = MediaTypes.PackageRepositoryAddRequest
  override val produces: MediaType = MediaTypes.PackageRepositoryAddResponse

  override def apply(
    request: PackageRepositoryAddRequest
  ): Future[PackageRepositoryAddResponse] = {
    request.uri.scheme match {
      case Some("http") | Some("https") =>
        sourcesStorage.add(
          request.index,
          PackageRepository(request.name, request.uri)
        ) map { sources =>
          PackageRepositoryAddResponse(sources)
        }
      case _ => throw UnsupportedRepositoryUri(request.uri)
    }
  }
}
