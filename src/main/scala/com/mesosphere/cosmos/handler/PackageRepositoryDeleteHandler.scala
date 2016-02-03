package com.mesosphere.cosmos.handler

import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

import com.mesosphere.cosmos.RepoNameOrUriMissing
import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import com.mesosphere.cosmos.model.PackageRepositoryDeleteRequest
import com.mesosphere.cosmos.model.PackageRepositoryDeleteResponse
import com.mesosphere.cosmos.model.PackageSource
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
    val nameFilter = request.name.map(name => (source: PackageSource) => source.name == name)
    val uriFilter = request.uri.map(uri => (source: PackageSource) => source.uri == uri)

    nameFilter.orElse(uriFilter) match {
      case Some(filterFn) =>
        sourcesStorage.read().flatMap { sources =>
          val updatedSources = sources.filterNot(filterFn)
          sourcesStorage.write(updatedSources)
        }.map(sources => PackageRepositoryDeleteResponse(sources))
      case None =>
        Future.exception(RepoNameOrUriMissing())
    }
  }

}
