package com.mesosphere.cosmos.handler

import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

import com.mesosphere.cosmos.RepoNameOrUriMissing
import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import com.mesosphere.cosmos.model.PackageRepositoryDeleteRequest
import com.mesosphere.cosmos.model.PackageRepositoryDeleteResponse
import com.mesosphere.cosmos.model.PackageRepository
import com.mesosphere.cosmos.repository.PackageSourcesStorage

private[cosmos] final class PackageRepositoryDeleteHandler(sourcesStorage: PackageSourcesStorage)(
  implicit
  decoder: DecodeRequest[PackageRepositoryDeleteRequest],
  encoder: Encoder[PackageRepositoryDeleteResponse]
) extends EndpointHandler[PackageRepositoryDeleteRequest, PackageRepositoryDeleteResponse] {

  override val accepts: MediaType = MediaTypes.PackageRepositoryDeleteRequest
  override val produces: MediaType = MediaTypes.PackageRepositoryDeleteResponse

  override def apply(
    request: PackageRepositoryDeleteRequest
  ): Future[PackageRepositoryDeleteResponse] = {
    sourcesStorage.delete(request.name, request.uri).map { sources =>
      PackageRepositoryDeleteResponse(sources)
    }
  }
}
