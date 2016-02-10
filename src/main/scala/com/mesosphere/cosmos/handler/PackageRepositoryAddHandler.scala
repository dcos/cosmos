package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import com.mesosphere.cosmos.model.PackageRepositoryAddRequest
import com.mesosphere.cosmos.model.PackageRepositoryAddResponse
import com.mesosphere.cosmos.model.PackageSource
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
    sourcesStorage.read().flatMap { sources =>
      val index = request.index.getOrElse(0)
      val (leftSources, rightSources) = sources.splitAt(index)
      val updatedSources = leftSources ++ (
        PackageSource(request.name, request.uri) :: rightSources
      )
      sourcesStorage.write(updatedSources)
    } map { sources =>
      PackageRepositoryAddResponse(sources)
    }
  }

}
