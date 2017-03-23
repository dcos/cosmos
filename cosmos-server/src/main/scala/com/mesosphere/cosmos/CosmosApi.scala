package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.finch._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.finch.FinchExtensions._
import com.mesosphere.cosmos.finch.RequestValidators
import com.mesosphere.cosmos.handler.CapabilitiesHandler
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedBodyParsers._
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedEncoders._
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedRequestDecoders._
import com.mesosphere.cosmos.rpc.v2.circe.MediaTypedEncoders._
import com.mesosphere.universe

final class CosmosApi(
  capabilitiesHandler: CapabilitiesHandler,
  packageAddHandler: EndpointHandler[rpc.v1.model.AddRequest, rpc.v1.model.AddResponse],
  packageDescribeHandler: EndpointHandler[rpc.v1.model.DescribeRequest, universe.v3.model.PackageDefinition],
  packageInstallHandler: EndpointHandler[rpc.v1.model.InstallRequest, rpc.v2.model.InstallResponse],
  packageListHandler: EndpointHandler[rpc.v1.model.ListRequest, rpc.v1.model.ListResponse],
  packageListVersionsHandler: EndpointHandler[rpc.v1.model.ListVersionsRequest, rpc.v1.model.ListVersionsResponse],
  packageRenderHandler: EndpointHandler[rpc.v1.model.RenderRequest, rpc.v1.model.RenderResponse],
  packageRepositoryAddHandler: EndpointHandler[rpc.v1.model.PackageRepositoryAddRequest, rpc.v1.model.PackageRepositoryAddResponse],
  packageRepositoryDeleteHandler: EndpointHandler[rpc.v1.model.PackageRepositoryDeleteRequest, rpc.v1.model.PackageRepositoryDeleteResponse],
  packageRepositoryListHandler: EndpointHandler[rpc.v1.model.PackageRepositoryListRequest, rpc.v1.model.PackageRepositoryListResponse],
  packageSearchHandler: EndpointHandler[rpc.v1.model.SearchRequest, rpc.v1.model.SearchResponse],
  packageUninstallHandler: EndpointHandler[rpc.v1.model.UninstallRequest, rpc.v1.model.UninstallResponse],
  serviceStartHandler: EndpointHandler[rpc.v1.model.ServiceStartRequest, rpc.v1.model.ServiceStartResponse]
) {

  // Package Handlers
  val packageInstall: Endpoint[Json] = {
    route(post("package" :: "install"), packageInstallHandler)(RequestValidators.standard)
  }

  val packageUninstall: Endpoint[Json] = {
    route(post("package" :: "uninstall"), packageUninstallHandler)(RequestValidators.standard)
  }

  val packageDescribe: Endpoint[Json] = {
    route(post("package" :: "describe"), packageDescribeHandler)(RequestValidators.standard)
  }

  val packageRender: Endpoint[Json] = {
    route(post("package" :: "render"), packageRenderHandler)(RequestValidators.standard)
  }

  val packageListVersions: Endpoint[Json] = {
    route(
      post("package" :: "list-versions"),
      packageListVersionsHandler
    )(RequestValidators.standard)
  }

  val packageSearch: Endpoint[Json] = {
    route(post("package" :: "search"), packageSearchHandler)(RequestValidators.standard)
  }

  val packageList: Endpoint[Json] = {
    route(post("package" :: "list"), packageListHandler)(RequestValidators.standard)
  }

  val packageRepositoryList: Endpoint[Json] = {
    route(
      post("package" :: "repository" :: "list"),
      packageRepositoryListHandler
    )(RequestValidators.standard)
  }

  val packageRepositoryAdd: Endpoint[Json] = {
    route(
      post("package" :: "repository" :: "add"),
      packageRepositoryAddHandler
    )(RequestValidators.standard)
  }

  val packageRepositoryDelete: Endpoint[Json] = {
    route(
      post("package" :: "repository" :: "delete"),
      packageRepositoryDeleteHandler
    )(RequestValidators.standard)
  }

  val packageAdd: Endpoint[Json] = {
    route(post("package" :: "add"), packageAddHandler)(RequestValidators.selectedBody)
  }

  // Service Handlers
  val serviceStart: Endpoint[Json] = {
    route(post("service" :: "start"), serviceStartHandler)(RequestValidators.standard)
  }

  // Capabilities
  val capabilities: Endpoint[Json] = {
    route(get("capabilities"), capabilitiesHandler)(RequestValidators.noBody)
  }

}
