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
  implicit val encodeInstallRequest: Encoder[InstallRequest] = deriveEncoder[InstallRequest]
  implicit val encodeInstallResponse: Encoder[InstallResponse] = deriveEncoder[InstallResponse]
  implicit val encodeUninstallRequest: Encoder[UninstallRequest] = deriveEncoder[UninstallRequest]
  implicit val encodeUninstallResponse: Encoder[UninstallResponse] = deriveEncoder[UninstallResponse]
  implicit val encodeUninstallResult: Encoder[UninstallResult] = deriveEncoder[UninstallResult]

  implicit val encodeRenderRequest: Encoder[RenderRequest] = deriveEncoder[RenderRequest]
  implicit val encodeRenderResponse: Encoder[RenderResponse] = deriveEncoder[RenderResponse]

  implicit val encodeDescribeResponse: Encoder[DescribeResponse] = deriveEncoder[DescribeResponse]
  implicit val encodeListVersionsRequest: Encoder[ListVersionsRequest] = deriveEncoder[ListVersionsRequest]
  implicit val encodeListVersionsResponse: Encoder[ListVersionsResponse] = deriveEncoder[ListVersionsResponse]

  implicit val encodeListRequest: Encoder[ListRequest] = deriveEncoder[ListRequest]
  implicit val encodeListResponse: Encoder[ListResponse] = deriveEncoder[ListResponse]
  implicit val encodeInstallation: Encoder[Installation] = deriveEncoder[Installation]
  implicit val encodeInstalledPackageInformationPackageDetails: Encoder[InstalledPackageInformationPackageDetails] = {
    import com.mesosphere.universe.v2.circe.Encoders._ // import implicits at as narrow a scope as possible
    deriveEncoder[InstalledPackageInformationPackageDetails]
  }
  implicit val encodePackageInformation: Encoder[InstalledPackageInformation] = deriveEncoder[InstalledPackageInformation]

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
  implicit val encodeUniverseAddRequest: Encoder[UniverseAddRequest] = {
    deriveEncoder[UniverseAddRequest]
  }
  implicit val encodeAddResponse: Encoder[AddResponse] = {
    implicitly[Encoder[universe.v3.model.PackageDefinition]].contramap(_.packageDefinition)
  }

  implicit val encodeErrorResponse: Encoder[ErrorResponse] = deriveEncoder[ErrorResponse]

  implicit val encodePackageCoordinate: Encoder[PackageCoordinate] =
    deriveEncoder[PackageCoordinate]

    /* This encoder converts a LocalPackage into a JSON object. The total number of fields are
     * enumerated below.
     *
     * {
     *   "status": <String>,
     *   "metadata": <PackageDefinition>,
     *   "operation": ..., // TODO: Update this after we merge the PackageOps PR.
     *   "error": <ErrorResponse>,
     *   "packageCoordinate": <PackageCoordinate>
     * }
     *
     * The 'status' will always be set while the rest of the properties are optional.
     */
  implicit val encodeLocalPackage = new Encoder[LocalPackage] {
    final override def apply(value: LocalPackage): Json = {
      val dataField = value.metadata.fold(
        pc => ("packageCoordinate", pc.asJson),
        pkg => ("metadata", pkg.asJson)
      )

      Json.obj(
        "status" -> Json.fromString(value.getClass.getSimpleName),
        dataField,
        "error" -> value.error.asJson,
        "operation" -> value.operation.asJson
      )
    }
  }

  implicit val encodeServiceStartRequest: Encoder[ServiceStartRequest] =
    deriveEncoder[ServiceStartRequest]
  implicit val encodeServiceStartResponse: Encoder[ServiceStartResponse] =
    deriveEncoder[ServiceStartResponse]
}
