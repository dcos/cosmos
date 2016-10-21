package com.mesosphere.cosmos.rpc.v1.circe

import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.cosmos.thirdparty.marathon.circe.Decoders._
import com.mesosphere.universe
import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.v2.circe.Decoders._
import com.mesosphere.universe.v2.model.{PackageDetailsVersion, ReleaseVersion}
import com.mesosphere.universe.v3.circe.Decoders._
import io.circe._
import io.circe.generic.semiauto._

object Decoders {

  implicit val keyDecodePackageDefinitionVersion: KeyDecoder[universe.v3.model.PackageDefinition.Version] = {
    KeyDecoder.instance { s => Some(universe.v3.model.PackageDefinition.Version(s)) }
  }

  implicit val decodeSearchResult: Decoder[SearchResult] = deriveDecoder[SearchResult]

  implicit val decodeDescribeRequest: Decoder[DescribeRequest] = deriveDecoder[DescribeRequest]
  implicit val decodeSearchRequest: Decoder[SearchRequest] = deriveDecoder[SearchRequest]
  implicit val decodeSearchResponse: Decoder[SearchResponse] = deriveDecoder[SearchResponse]
  implicit val decodeInstallRequest: Decoder[InstallRequest] = deriveDecoder[InstallRequest]
  implicit val decodeInstallResponse: Decoder[InstallResponse] = deriveDecoder[InstallResponse]
  implicit val decodeUninstallRequest: Decoder[UninstallRequest] = deriveDecoder[UninstallRequest]
  implicit val decodeUninstallResponse: Decoder[UninstallResponse] = deriveDecoder[UninstallResponse]
  implicit val decodeUninstallResult: Decoder[UninstallResult] = deriveDecoder[UninstallResult]

  implicit val decodeRenderRequest: Decoder[RenderRequest] = deriveDecoder[RenderRequest]
  implicit val decodeRenderResponse: Decoder[RenderResponse] = deriveDecoder[RenderResponse]

  implicit val decodeDescribeResponse: Decoder[DescribeResponse] = deriveDecoder[DescribeResponse]
  implicit val decodeListVersionsRequest: Decoder[ListVersionsRequest] = deriveDecoder[ListVersionsRequest]
  implicit val decodeListVersionsResponse: Decoder[ListVersionsResponse] = deriveDecoder[ListVersionsResponse]

  implicit val decodeListRequest: Decoder[ListRequest] = deriveDecoder[ListRequest]
  implicit val decodeListResponse: Decoder[ListResponse] = deriveDecoder[ListResponse]
  implicit val decodeInstallation: Decoder[Installation] = deriveDecoder[Installation]
  implicit val decodeInstalledPackageInformationPackageDetails: Decoder[InstalledPackageInformationPackageDetails] = deriveDecoder[InstalledPackageInformationPackageDetails]
  implicit val decodePackageInformation: Decoder[InstalledPackageInformation] = deriveDecoder[InstalledPackageInformation]

  implicit val decodeCapabilitiesResponse: Decoder[CapabilitiesResponse] = deriveDecoder[CapabilitiesResponse]
  implicit val decodeCapability: Decoder[Capability] = deriveDecoder[Capability]

  implicit val decodePackageRepositoryListRequest: Decoder[PackageRepositoryListRequest] = {
    deriveDecoder[PackageRepositoryListRequest]
  }
  implicit val decodePackageRepositoryListResponse: Decoder[PackageRepositoryListResponse] = {
    deriveDecoder[PackageRepositoryListResponse]
  }
  implicit val decodePackageRepository: Decoder[PackageRepository] = {
    deriveDecoder[PackageRepository]
  }
  implicit val decodePackageRepositoryAddRequest: Decoder[PackageRepositoryAddRequest] = {
    deriveDecoder[PackageRepositoryAddRequest]
  }
  implicit val decodePackageRepositoryAddResponse: Decoder[PackageRepositoryAddResponse] = {
    deriveDecoder[PackageRepositoryAddResponse]
  }
  implicit val decodePackageRepositoryDeleteRequest: Decoder[PackageRepositoryDeleteRequest] = {
    deriveDecoder[PackageRepositoryDeleteRequest]
  }
  implicit val decodePackageRepositoryDeleteResponse: Decoder[PackageRepositoryDeleteResponse] = {
    deriveDecoder[PackageRepositoryDeleteResponse]
  }

  implicit val decodePackageCoordinate: Decoder[PackageCoordinate] =
    deriveDecoder[PackageCoordinate]
}
