package com.mesosphere.cosmos.rpc.v1.circe

import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.universe
import com.mesosphere.universe.common.circe.Encoders._
import com.mesosphere.universe.v2.circe.Encoders._
import com.mesosphere.universe.v3.circe.Encoders.{encodeImages => encodeV3Images}
import com.mesosphere.universe.v3.circe.Encoders.{encodeLicense => encodeV3License}
import com.mesosphere.universe.v3.circe.Encoders._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

object Encoders {

  implicit val keyEncodePackageDefinitionVersion: KeyEncoder[universe.v3.model.PackageDefinition.Version] = {
    KeyEncoder.instance(_.toString)
  }
  implicit val encodePackageDefinitionReleaseVersion: Encoder[universe.v3.model.PackageDefinition.ReleaseVersion] = {
    Encoder.instance { rv => rv.value.asJson }
  }

  implicit val encodeSearchResult: Encoder[SearchResult] = deriveEncoder[SearchResult]

  implicit val encodeDescribeRequest: Encoder[DescribeRequest] = deriveEncoder[DescribeRequest]
  implicit val encodeSearchRequest: Encoder[SearchRequest] = deriveEncoder[SearchRequest]
  implicit val encodeSearchResponse: Encoder[SearchResponse] = deriveEncoder[SearchResponse]
  implicit val encodeRunRequest: Encoder[RunRequest] = deriveEncoder[RunRequest]
  implicit val encodeRunResponse: Encoder[RunResponse] = deriveEncoder[RunResponse]
  implicit val encodeKillRequest: Encoder[KillRequest] = deriveEncoder[KillRequest]
  implicit val encodeKillResponse: Encoder[KillResponse] = deriveEncoder[KillResponse]
  implicit val encodeKillResult: Encoder[KillResult] = deriveEncoder[KillResult]

  implicit val encodeRenderRequest: Encoder[RenderRequest] = deriveEncoder[RenderRequest]
  implicit val encodeRenderResponse: Encoder[RenderResponse] = deriveEncoder[RenderResponse]

  implicit val encodeDescribeResponse: Encoder[DescribeResponse] = deriveEncoder[DescribeResponse]
  implicit val encodeListVersionsRequest: Encoder[ListVersionsRequest] = deriveEncoder[ListVersionsRequest]
  implicit val encodeListVersionsResponse: Encoder[ListVersionsResponse] = deriveEncoder[ListVersionsResponse]

  implicit val encodeListRequest: Encoder[ListRequest] = deriveEncoder[ListRequest]
  implicit val encodeListResponse: Encoder[ListResponse] = deriveEncoder[ListResponse]
  implicit val encodeInstantiation: Encoder[Instantiation] = deriveEncoder[Instantiation]
  implicit val encodeRunningPackageInformationPackageDetails: Encoder[RunningPackageInformationPackageDetails] = {
    import com.mesosphere.universe.v2.circe.Encoders._ // import implicits at as narrow a scope as possible
    deriveEncoder[RunningPackageInformationPackageDetails]
  }
  implicit val encodePackageInformation: Encoder[RunningPackageInformation] = deriveEncoder[RunningPackageInformation]

  implicit val encodeCapabilitiesResponse: Encoder[CapabilitiesResponse] = deriveEncoder[CapabilitiesResponse]
  implicit val encodeCapability: Encoder[Capability] = deriveEncoder[Capability]

  implicit val encodePackageRepositoryListRequest: Encoder[PackageRepositoryListRequest] = {
    deriveEncoder[PackageRepositoryListRequest]
  }
  implicit val encodePackageRepositoryListResponse: Encoder[PackageRepositoryListResponse] = {
    deriveEncoder[PackageRepositoryListResponse]
  }
  implicit val encodePackageRepository: Encoder[PackageRepository] = {
    deriveEncoder[PackageRepository]
  }
  implicit val encodePackageRepositoryAddRequest: Encoder[PackageRepositoryAddRequest] = {
    deriveEncoder[PackageRepositoryAddRequest]
  }
  implicit val encodePackageRepositoryAddResponse: Encoder[PackageRepositoryAddResponse] = {
    deriveEncoder[PackageRepositoryAddResponse]
  }
  implicit val encodePackageRepositoryDeleteRequest: Encoder[PackageRepositoryDeleteRequest] = {
    deriveEncoder[PackageRepositoryDeleteRequest]
  }
  implicit val encodePackageRepositoryDeleteResponse: Encoder[PackageRepositoryDeleteResponse] = {
    deriveEncoder[PackageRepositoryDeleteResponse]
  }

  implicit val encodeErrorResponse: Encoder[ErrorResponse] = deriveEncoder[ErrorResponse]
}
