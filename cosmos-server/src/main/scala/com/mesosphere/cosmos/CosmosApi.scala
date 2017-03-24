package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.finch._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.finch.FinchExtensions._
import com.mesosphere.cosmos.finch.RequestValidators
import com.mesosphere.cosmos.handler.CapabilitiesHandler
import com.mesosphere.cosmos.handler.ListHandler
import com.mesosphere.cosmos.handler.ListVersionsHandler
import com.mesosphere.cosmos.handler.NotConfiguredHandler
import com.mesosphere.cosmos.handler.PackageAddHandler
import com.mesosphere.cosmos.handler.PackageDescribeHandler
import com.mesosphere.cosmos.handler.PackageInstallHandler
import com.mesosphere.cosmos.handler.PackageRenderHandler
import com.mesosphere.cosmos.handler.PackageRepositoryAddHandler
import com.mesosphere.cosmos.handler.PackageRepositoryDeleteHandler
import com.mesosphere.cosmos.handler.PackageRepositoryListHandler
import com.mesosphere.cosmos.handler.PackageSearchHandler
import com.mesosphere.cosmos.handler.ServiceStartHandler
import com.mesosphere.cosmos.handler.UninstallHandler
import com.mesosphere.cosmos.repository.LocalPackageCollection
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedBodyParsers._
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedEncoders._
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedRequestDecoders._
import com.mesosphere.cosmos.rpc.v2.circe.MediaTypedEncoders._
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.storage.installqueue.ProducerView

final class CosmosApi(
  val adminRouter: AdminRouter,
  val sourcesStorage: PackageSourcesStorage,
  val objectStorages: Option[(LocalPackageCollection, StagedPackageStorage)],
  val repositories: MultiRepository,
  val producerView: ProducerView,
  val packageRunner: PackageRunner
) {

  import CosmosApi._

  // Package Handlers
  val packageInstall: Endpoint[Json] = {
    val handler = new PackageInstallHandler(repositories, packageRunner)
    route(post("package" :: "install"), handler)(RequestValidators.standard)
  }

  val packageUninstall: Endpoint[Json] = {
    val handler = new UninstallHandler(adminRouter, repositories)
    route(post("package" :: "uninstall"), handler)(RequestValidators.standard)
  }

  val packageDescribe: Endpoint[Json] = {
    val handler = new PackageDescribeHandler(repositories)
    route(post("package" :: "describe"), handler)(RequestValidators.standard)
  }

  val packageRender: Endpoint[Json] = {
    val handler = new PackageRenderHandler(repositories)
    route(post("package" :: "render"), handler)(RequestValidators.standard)
  }

  val packageListVersions: Endpoint[Json] = {
    val handler = new ListVersionsHandler(repositories)
    route(post("package" :: "list-versions"), handler)(RequestValidators.standard)
  }

  val packageSearch: Endpoint[Json] = {
    val handler = new PackageSearchHandler(repositories)
    route(post("package" :: "search"), handler)(RequestValidators.standard)
  }

  val packageList: Endpoint[Json] = {
    val handler = new ListHandler(adminRouter, uri => repositories.getRepository(uri))
    route(post("package" :: "list"), handler)(RequestValidators.standard)
  }

  val packageRepositoryList: Endpoint[Json] = {
    val handler = new PackageRepositoryListHandler(sourcesStorage)
    route(post("package" :: "repository" :: "list"), handler)(RequestValidators.standard)
  }

  val packageRepositoryAdd: Endpoint[Json] = {
    val handler = new PackageRepositoryAddHandler(sourcesStorage)
    route(post("package" :: "repository" :: "add"), handler)(RequestValidators.standard)
  }

  val packageRepositoryDelete: Endpoint[Json] = {
    val handler = new PackageRepositoryDeleteHandler(sourcesStorage)
    route(post("package" :: "repository" :: "delete"), handler)(RequestValidators.standard)
  }

  val packageAdd: Endpoint[Json] = {
    val handler = enableIfSome(objectStorages, "package add") {
      case (_, stagedStorage) => new PackageAddHandler(repositories, stagedStorage, producerView)
    }

    route(post("package" :: "add"), handler)(RequestValidators.selectedBody)
  }

  // Service Handlers
  val serviceStart: Endpoint[Json] = {
    val handler = enableIfSome(objectStorages, "service start") {
      case (localPackageCollection, _) =>
        new ServiceStartHandler(localPackageCollection, packageRunner)
    }

    route(post("service" :: "start"), handler)(RequestValidators.standard)
  }

  // Capabilities
  val capabilities: Endpoint[Json] = {
    val handler = new CapabilitiesHandler
    route(get("capabilities"), handler)(RequestValidators.noBody)
  }

  // Keep alphabetized
  val allEndpoints = (
    capabilities
      :+: packageAdd
      :+: packageDescribe
      :+: packageInstall
      :+: packageList
      :+: packageListVersions
      :+: packageRender
      :+: packageRepositoryAdd
      :+: packageRepositoryDelete
      :+: packageRepositoryList
      :+: packageSearch
      :+: packageUninstall
      :+: serviceStart
    )

}

object CosmosApi {

  private def enableIfSome[A, Req, Res](requirement: Option[A], operationName: String)(
    f: A => EndpointHandler[Req, Res]
  ): EndpointHandler[Req, Res] = {
    requirement.fold[EndpointHandler[Req, Res]](new NotConfiguredHandler(operationName))(f)
  }

}
