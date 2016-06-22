package com.mesosphere.cosmos.rpc.v1.circe

import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.cosmos.thirdparty.marathon.circe.Decoders._
import com.mesosphere.universe
import com.mesosphere.universe.common.circe.Decoders._
import com.mesosphere.universe.v2.circe.Decoders._
import com.mesosphere.universe.v2.model.{PackageDetailsVersion, ReleaseVersion}
import com.mesosphere.universe.v3.circe.Decoders._
import io.circe.generic.semiauto._
import io.circe.{Decoder, HCursor}

object Decoders {

  implicit val decodeVersionsMap: Decoder[Map[universe.v3.model.PackageDefinition.Version, universe.v3.model.PackageDefinition.ReleaseVersion]] = {
    Decoder.decodeMap[Map, universe.v3.model.PackageDefinition.ReleaseVersion].map { stringKeys =>
      stringKeys.map { case (versionString, releaseVersion) =>
        universe.v3.model.PackageDefinition.Version(versionString) -> releaseVersion
      }
    }
  }

  implicit val decodeSearchResult: Decoder[SearchResult] = deriveFor[SearchResult].decoder

  implicit val decodeDescribeRequest: Decoder[DescribeRequest] = deriveFor[DescribeRequest].decoder
  implicit val decodeSearchRequest: Decoder[SearchRequest] = deriveFor[SearchRequest].decoder
  implicit val decodeSearchResponse: Decoder[SearchResponse] = deriveFor[SearchResponse].decoder
  implicit val decodeInstallRequest: Decoder[InstallRequest] = deriveFor[InstallRequest].decoder
  implicit val decodeInstallResponse: Decoder[InstallResponse] = deriveFor[InstallResponse].decoder
  implicit val decodeUninstallRequest: Decoder[UninstallRequest] = deriveFor[UninstallRequest].decoder
  implicit val decodeUninstallResponse: Decoder[UninstallResponse] = deriveFor[UninstallResponse].decoder
  implicit val decodeUninstallResult: Decoder[UninstallResult] = deriveFor[UninstallResult].decoder

  implicit val decodeRenderRequest: Decoder[RenderRequest] = deriveFor[RenderRequest].decoder
  implicit val decodeRenderResponse: Decoder[RenderResponse] = deriveFor[RenderResponse].decoder

  implicit val decodeDescribeResponse: Decoder[DescribeResponse] = deriveFor[DescribeResponse].decoder
  implicit val decodeListVersionsRequest: Decoder[ListVersionsRequest] = deriveFor[ListVersionsRequest].decoder
  implicit val decodeListVersionsResponse: Decoder[ListVersionsResponse] = Decoder.instance { (cursor: HCursor) =>
    for {
      r <- cursor.downField("results").as[Map[String, String]]
    } yield {
      val results = r.map { case (s1, s2) =>
        PackageDetailsVersion(s1) -> ReleaseVersion(s2)
      }
      ListVersionsResponse(results)
    }
  }

  implicit val decodeListRequest: Decoder[ListRequest] = deriveFor[ListRequest].decoder
  implicit val decodeListResponse: Decoder[ListResponse] = deriveFor[ListResponse].decoder
  implicit val decodeInstallation: Decoder[Installation] = deriveFor[Installation].decoder
  implicit val decodePackageInformation: Decoder[InstalledPackageInformation] = deriveFor[InstalledPackageInformation].decoder

  implicit val decodeCapabilitiesResponse: Decoder[CapabilitiesResponse] = deriveFor[CapabilitiesResponse].decoder
  implicit val decodeCapability: Decoder[Capability] = deriveFor[Capability].decoder

  implicit val decodePackageRepositoryListRequest: Decoder[PackageRepositoryListRequest] = {
    deriveFor[PackageRepositoryListRequest].decoder
  }
  implicit val decodePackageRepositoryListResponse: Decoder[PackageRepositoryListResponse] = {
    deriveFor[PackageRepositoryListResponse].decoder
  }
  implicit val decodePackageRepository: Decoder[PackageRepository] = {
    deriveFor[PackageRepository].decoder
  }
  implicit val decodePackageRepositoryAddRequest: Decoder[PackageRepositoryAddRequest] = {
    deriveFor[PackageRepositoryAddRequest].decoder
  }
  implicit val decodePackageRepositoryAddResponse: Decoder[PackageRepositoryAddResponse] = {
    deriveFor[PackageRepositoryAddResponse].decoder
  }
  implicit val decodePackageRepositoryDeleteRequest: Decoder[PackageRepositoryDeleteRequest] = {
    deriveFor[PackageRepositoryDeleteRequest].decoder
  }
  implicit val decodePackageRepositoryDeleteResponse: Decoder[PackageRepositoryDeleteResponse] = {
    deriveFor[PackageRepositoryDeleteResponse].decoder
  }

}
