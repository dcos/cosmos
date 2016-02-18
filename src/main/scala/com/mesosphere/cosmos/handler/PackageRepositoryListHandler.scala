package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.MultiRepository
import com.mesosphere.cosmos.http.{MediaType, MediaTypes}
import com.mesosphere.cosmos.model._
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

private[cosmos] final class PackageRepositoryListHandler(
  multiRepository: MultiRepository
)(implicit
  decoder: DecodeRequest[PackageRepositoryListRequest],
  encoder: Encoder[PackageRepositoryListResponse]
) extends EndpointHandler[PackageRepositoryListRequest, PackageRepositoryListResponse] {

  override val accepts: MediaType = MediaTypes.PackageRepositoryListRequest
  override val produces: MediaType = MediaTypes.PackageRepositoryListResponse

  override def apply(req: PackageRepositoryListRequest): Future[PackageRepositoryListResponse] = {
    multiRepository.repositoryMetadata().map(PackageRepositoryListResponse(_))
  }
}
