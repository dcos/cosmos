package com.mesosphere.cosmos.rpc.v1.circe

import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.universe
import com.mesosphere.universe.v2.circe.Encoders._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

object Encoders {
  implicit val keyEncodePackageDefinitionVersion: KeyEncoder[universe.v3.model.Version] = {
    KeyEncoder.instance(_.toString)
  }
  implicit val encodePackageDefinitionReleaseVersion: Encoder[universe.v3.model.ReleaseVersion] = {
    Encoder.instance { rv => rv.value.asJson }
  }

  implicit val encodeSearchResult: Encoder[SearchResult] = deriveEncoder[SearchResult]

  implicit val encodeDescribeRequest: Encoder[DescribeRequest] = deriveEncoder[DescribeRequest]
  implicit val encodeSearchRequest: Encoder[SearchRequest] = deriveEncoder[SearchRequest]
  implicit val encodeSearchResponse: Encoder[SearchResponse] = deriveEncoder[SearchResponse]
  implicit val encodeInstallRequest: Encoder[InstallRequest] = deriveEncoder[InstallRequest]
  implicit val encodeInstallResponse: Encoder[InstallResponse] = deriveEncoder[InstallResponse]
  implicit val encodeUninstallRequest: Encoder[UninstallRequest] = deriveEncoder[UninstallRequest]
  implicit val encodeUninstallResponse: Encoder[UninstallResponse] = deriveEncoder[UninstallResponse]
  implicit val encodeUninstallResult: Encoder[UninstallResult] = deriveEncoder[UninstallResult]

  implicit val encodeRenderRequest: Encoder[RenderRequest] = deriveEncoder[RenderRequest]
  implicit val encodeRenderResponse: Encoder[RenderResponse] = deriveEncoder[RenderResponse]

  implicit val encodeListVersionsRequest: Encoder[ListVersionsRequest] = deriveEncoder[ListVersionsRequest]
  implicit val encodeListVersionsResponse: Encoder[ListVersionsResponse] = deriveEncoder[ListVersionsResponse]

  implicit val encodeListRequest: Encoder[ListRequest] = deriveEncoder[ListRequest]
  implicit val encodeListResponse: Encoder[ListResponse] = deriveEncoder[ListResponse]
  implicit val encodeInstallation: Encoder[Installation] = deriveEncoder[Installation]
  implicit val encodeInstalledPackageInformationPackageDetails: Encoder[InstalledPackageInformationPackageDetails] = {
    deriveEncoder[InstalledPackageInformationPackageDetails]
  }
  implicit val encodePackageInformation: Encoder[InstalledPackageInformation] = deriveEncoder[InstalledPackageInformation]

  implicit val encodeCapabilitiesResponse: Encoder[CapabilitiesResponse] = deriveEncoder[CapabilitiesResponse]
  implicit val encodeCapability: Encoder[Capability] = deriveEncoder[Capability]

  implicit val encodePackageRepositoryListResponse: Encoder[PackageRepositoryListResponse] = {
    deriveEncoder[PackageRepositoryListResponse]
  }
  implicit val encodePackageRepositoryAddResponse: Encoder[PackageRepositoryAddResponse] = {
    deriveEncoder[PackageRepositoryAddResponse]
  }
  implicit val encodePackageRepositoryDeleteResponse: Encoder[PackageRepositoryDeleteResponse] = {
    deriveEncoder[PackageRepositoryDeleteResponse]
  }

  implicit val encodePackageCoordinate: Encoder[PackageCoordinate] =
    deriveEncoder[PackageCoordinate]

}
