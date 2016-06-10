package com.mesosphere.cosmos.rpc.v1.circe

import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.universe.common.circe.Encoders._
import com.mesosphere.universe.v2.circe.Encoders._
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.{Encoder, JsonObject, ObjectEncoder}

object Encoders {

  implicit val encodeSearchResult: Encoder[SearchResult] = ObjectEncoder.instance { searchResult =>
    val encodedFields = encodeIndexEntryFields(
      searchResult.name,
      searchResult.currentVersion,
      searchResult.versions,
      searchResult.description,
      searchResult.framework,
      searchResult.tags,
      searchResult.selected
    )
    JsonObject.fromIndexedSeq(encodedFields :+ ("images" -> searchResult.images.asJson))
  }

  implicit val encodeDescribeRequest: Encoder[DescribeRequest] = deriveFor[DescribeRequest].encoder
  implicit val encodeSearchRequest: Encoder[SearchRequest] = deriveFor[SearchRequest].encoder
  implicit val encodeSearchResponse: Encoder[SearchResponse] = deriveFor[SearchResponse].encoder
  implicit val encodeInstallRequest: Encoder[InstallRequest] = deriveFor[InstallRequest].encoder
  implicit val encodeInstallResponse: Encoder[InstallResponse] = deriveFor[InstallResponse].encoder
  implicit val encodeUninstallRequest: Encoder[UninstallRequest] = deriveFor[UninstallRequest].encoder
  implicit val encodeUninstallResponse: Encoder[UninstallResponse] = deriveFor[UninstallResponse].encoder
  implicit val encodeUninstallResult: Encoder[UninstallResult] = deriveFor[UninstallResult].encoder

  implicit val encodeRenderRequest: Encoder[RenderRequest] = deriveFor[RenderRequest].encoder
  implicit val encodeRenderResponse: Encoder[RenderResponse] = deriveFor[RenderResponse].encoder

  implicit val encodeDescribeResponse: Encoder[DescribeResponse] = deriveFor[DescribeResponse].encoder
  implicit val encodeListVersionsRequest: Encoder[ListVersionsRequest] = deriveFor[ListVersionsRequest].encoder
  implicit val encodeListVersionsResponse: Encoder[ListVersionsResponse] = ObjectEncoder.instance( response => {
    JsonObject.singleton("results", encodePackageDetailsVersionToReleaseVersionMap(response.results))
  })

  implicit val encodeListRequest: Encoder[ListRequest] = deriveFor[ListRequest].encoder
  implicit val encodeListResponse: Encoder[ListResponse] = deriveFor[ListResponse].encoder
  implicit val encodeInstallation: Encoder[Installation] = deriveFor[Installation].encoder
  implicit val encodePackageInformation: Encoder[InstalledPackageInformation] = deriveFor[InstalledPackageInformation].encoder

  implicit val encodeCapabilitiesResponse: Encoder[CapabilitiesResponse] = deriveFor[CapabilitiesResponse].encoder
  implicit val encodeCapability: Encoder[Capability] = deriveFor[Capability].encoder

  implicit val encodePackageRepositoryListRequest: Encoder[PackageRepositoryListRequest] = {
    deriveFor[PackageRepositoryListRequest].encoder
  }
  implicit val encodePackageRepositoryListResponse: Encoder[PackageRepositoryListResponse] = {
    deriveFor[PackageRepositoryListResponse].encoder
  }
  implicit val encodePackageRepository: Encoder[PackageRepository] = {
    deriveFor[PackageRepository].encoder
  }
  implicit val encodePackageRepositoryAddRequest: Encoder[PackageRepositoryAddRequest] = {
    deriveFor[PackageRepositoryAddRequest].encoder
  }
  implicit val encodePackageRepositoryAddResponse: Encoder[PackageRepositoryAddResponse] = {
    deriveFor[PackageRepositoryAddResponse].encoder
  }
  implicit val encodePackageRepositoryDeleteRequest: Encoder[PackageRepositoryDeleteRequest] = {
    deriveFor[PackageRepositoryDeleteRequest].encoder
  }
  implicit val encodePackageRepositoryDeleteResponse: Encoder[PackageRepositoryDeleteResponse] = {
    deriveFor[PackageRepositoryDeleteResponse].encoder
  }

}
