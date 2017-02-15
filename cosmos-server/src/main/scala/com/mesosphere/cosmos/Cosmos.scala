package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.finch._
import _root_.io.finch.circe.dropNullKeys._
import com.mesosphere.cosmos.app.Logging
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.finch.FinchExtensions._
import com.mesosphere.cosmos.finch.RequestValidators
import com.mesosphere.cosmos.handler._
import com.mesosphere.cosmos.repository.DefaultInstaller
import com.mesosphere.cosmos.repository.DefaultUniverseInstaller
import com.mesosphere.cosmos.repository.LocalPackageCollection
import com.mesosphere.cosmos.repository.OperationProcessor
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.repository.SyncFutureLeader
import com.mesosphere.cosmos.repository.Uninstaller
import com.mesosphere.cosmos.repository.UniverseClient
import com.mesosphere.cosmos.repository.ZkRepositoryList
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedBodyParsers._
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedEncoders._
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedRequestDecoders._
import com.mesosphere.cosmos.rpc.v2.circe.MediaTypedEncoders._
import com.mesosphere.cosmos.storage.GarbageCollector
import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.cosmos.storage.PackageStorage
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.storage.installqueue.InstallQueue
import com.mesosphere.cosmos.storage.installqueue.ReaderView
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.app.App
import com.twitter.app.Flag
import com.twitter.finagle.Http
import com.twitter.finagle.ListeningServer
import com.twitter.finagle.Service
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.finagle.http.filter.LoggingFilter
import com.twitter.finagle.param.Label
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.server.Admin
import com.twitter.server.AdminHttpServer
import com.twitter.server.Lifecycle
import com.twitter.server.Stats
import com.twitter.util.Await
import com.twitter.util.Try
import org.slf4j.Logger
import scala.concurrent.duration._


private[cosmos] final class Cosmos(
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
)(implicit statsReceiver: StatsReceiver = NullStatsReceiver) {

  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(classOf[Cosmos])

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

  val service: Service[Request, Response] = {
    val stats = statsReceiver.scope("errorFilter")

    val endpoints = (
      // Keep alphabetized
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

    val api = endpoints.handle {
      case re: CosmosError =>
        stats.counter(s"definedError/${sanitiseClassName(re.getClass)}").incr()
        val output = Output.failure(
          re,
          re.status
        ).withHeader(
          Fields.ContentType -> MediaTypes.ErrorResponse.show
        )
        re.getHeaders.foldLeft(output) { case (out, kv) => out.withHeader(kv) }
      case fe @ (_: _root_.io.finch.Error | _: _root_.io.finch.Errors) =>
        stats.counter(s"finchError/${sanitiseClassName(fe.getClass)}").incr()
        Output.failure(
          fe.asInstanceOf[Exception], // Must be an Exception based on the types
          Status.BadRequest
        ).withHeader(
          Fields.ContentType -> MediaTypes.ErrorResponse.show
        )
      case e: Exception =>
        stats.counter(s"unhandledException/${sanitiseClassName(e.getClass)}").incr()
        logger.warn("Unhandled exception: ", e)
        Output.failure(
          e,
          Status.InternalServerError
        ).withHeader(
          Fields.ContentType -> MediaTypes.ErrorResponse.show
        )
      case t: Throwable =>
        stats.counter(s"unhandledThrowable/${sanitiseClassName(t.getClass)}").incr()
        logger.warn("Unhandled throwable: ", t)
        Output.failure(
          new Exception(t),
          Status.InternalServerError
        ).withHeader(
          Fields.ContentType -> MediaTypes.ErrorResponse.show
        )
    }

    api.toServiceAs[Application.Json]
  }

  /**
    * Removes characters from class names that are disallowed by some metrics systems.
    *
    * @param clazz the class whose name is to be santised
    * @return The name of the specified class with all "illegal characters" replaced with '.'
    */
  private[this] def sanitiseClassName(clazz: Class[_]): String = {
    clazz.getName.replaceAllLiterally("$", ".")
  }

}

object Cosmos
extends App
with AdminHttpServer
with Admin
with Lifecycle
with Lifecycle.Warmup
with Stats
with Logging {

  lazy val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def main(): Unit = {
    val service = startCosmos()(statsReceiver)
    val maybeHttpServer = startServer(service)
    val maybeHttpsServer = startTlsServer(service)

    // Log and close on exit
    for (httpServer <- maybeHttpServer) {
      logger.info(s"HTTP server started on ${httpServer.boundAddress}")
      closeOnExit(httpServer)
    }

    // Log and close on exit
    for (httpServer <- maybeHttpsServer) {
      logger.info(s"HTTPS server started on ${httpServer.boundAddress}")
      closeOnExit(httpServer)
    }

    // Wait for the listeners to return
    for (httpServer <- maybeHttpServer) {
      Await.result(httpServer)
    }
    for (httpServer <- maybeHttpsServer) {
      Await.result(httpServer)
    }
  }

  private[this] def startServer(service: Service[Request, Response]): Option[ListeningServer] = {
    getHttpInterface.map { iface =>
      Http
        .server
        .configured(Label("http"))
        .serve(iface, service)
    }
  }

  private[this] def startTlsServer(
    service: Service[Request, Response]
  ): Option[ListeningServer] = {
    getHttpsInterface.map { iface =>
      Http
        .server
        .configured(Label("https"))
        .withTransport.tls(
          getCertificatePath.get.toString,
          getKeyPath.get.toString,
          None,
          None,
          None
        )
        .serve(iface, service)
    }
  }

  private[this] def startCosmos()(implicit stats: StatsReceiver): Service[Request, Response] = {
    HttpProxySupport.configureProxySupport()

    val adminRouter = configureDcosClients().get

    val zkUri = zookeeperUri()
    logger.info("Using {} for the ZooKeeper connection", zkUri)

    val zkClient = zookeeper.Clients.createAndInitialize(zkUri)
    onExit(zkClient.close())

    val repoList = ZkRepositoryList(zkClient)
    onExit(repoList.close())

    val installQueue = InstallQueue(zkClient)
    onExit(installQueue.close())

    val objectStorages = configureObjectStorage(installQueue)

    for ((packageStorage, stageStorage, _) <- objectStorages) {
      val processingLeader = SyncFutureLeader(
        zkClient,
        OperationProcessor(
          installQueue,
          DefaultInstaller(stageStorage, packageStorage),
          DefaultUniverseInstaller(packageStorage),
          Uninstaller.Noop
        ),
        GarbageCollector(
          stageStorage,
          installQueue,
          1.hour
        )
      )
      onExit(processingLeader.close())
    }

    LoggingFilter.andThen(
      Cosmos(
        adminRouter,
        new MarathonPackageRunner(adminRouter),
        repoList,
        UniverseClient(adminRouter),
        installQueue,
        objectStorages.map {
          case (_, stagedStorage, localCollection) =>
            (localCollection, stagedStorage)
        }
      ).service
    )
  }

  private[this] def configureDcosClients(): Try[AdminRouter] = {
    import com.netaporter.uri.dsl._

    Try(dcosUri())
      .map { dh =>
        val dcosHost: String = Uris.stripTrailingSlash(dh)
        logger.info("Connecting to DCOS Cluster at: {}", dcosHost)
        val adminRouter: Uri = dcosHost
        val mar: Uri = dcosHost / "marathon"
        val master: Uri = dcosHost / "mesos"
        (adminRouter, mar, master)
      }
      .handle {
        case _: IllegalArgumentException =>
          val adminRouter: Uri = adminRouterUri().toStringRaw
          val mar: Uri = marathonUri().toStringRaw
          val master: Uri = mesosMasterUri().toStringRaw
          logger.info("Connecting to Marathon at: {}", mar)
          logger.info("Connecting to Mesos master at: {}", master)
          logger.info("Connecting to AdminRouter at: {}", adminRouter)
          (adminRouter, mar, master)
      }
      .flatMap { case (adminRouterUri, marathonUri, mesosMasterUri) =>
        Trys.join(
          Services.adminRouterClient(
            adminRouterUri,
            maxClientResponseSize()
          ).map(adminRouterUri -> _),
          Services.marathonClient(
            marathonUri,
            maxClientResponseSize()
          ).map(marathonUri -> _),
          Services.mesosClient(
            mesosMasterUri,
            maxClientResponseSize()
          ).map(mesosMasterUri -> _)
        )
      }
      .map { case (adminRouter, marathon, mesosMaster) =>
        new AdminRouter(
          new AdminRouterClient(adminRouter._1, adminRouter._2),
          new MarathonClient(marathon._1, marathon._2),
          new MesosMasterClient(mesosMaster._1, mesosMaster._2)
        )
      }
  }

  private[this] def configureObjectStorage(
    installQueue: ReaderView
  )(
    implicit statsReceiver: StatsReceiver
  ): Option[(PackageStorage, StagedPackageStorage, LocalPackageCollection)] = {
    def fromFlag(
      flag: Flag[ObjectStorageUri],
      description: String
    ): Option[ObjectStorage] = {
      val value = flag.get.map(_.toString).getOrElse("None")
      logger.info(s"Using {} for the $description storage URI", value)
      flag.get.map(uri => ObjectStorage.fromUri(uri)(statsReceiver))
    }

    (
      fromFlag(packageStorageUri, "package").map(PackageStorage(_)),
      fromFlag(stagedPackageStorageUri, "staged package").map(StagedPackageStorage(_))
    ) match {
      case (Some(packageStorage), Some(stagedStorage)) =>
        Some((packageStorage, stagedStorage, LocalPackageCollection(packageStorage, installQueue)))
      case (None, None) =>
        None
      case (Some(_), None) =>
        throw new IllegalArgumentException(
          "Missing staged storage configuration. Staged storage configuration required if " +
          "package storage provided."
        )
      case (None, Some(_)) =>
        throw new IllegalArgumentException(
          "Missing package storage configuration. Package storage configuration required if " +
          "stage storage provided."
        )
    }
  }

  private[cosmos] def apply(
    adminRouter: AdminRouter,
    packageRunner: PackageRunner,
    sourcesStorage: PackageSourcesStorage,
    universeClient: UniverseClient,
    installQueue: InstallQueue,
    objectStorages: Option[(LocalPackageCollection, StagedPackageStorage)]
  )(implicit statsReceiver: StatsReceiver = NullStatsReceiver): Cosmos = {

    val repositories = new MultiRepository(
      sourcesStorage,
      universeClient
    )

    val packageAddHandler = enableIfSome(objectStorages, "package add") {
      case (_, stagedStorage) => new PackageAddHandler(repositories, stagedStorage, installQueue)
    }

    val serviceStartHandler = enableIfSome(objectStorages, "service start") {
      case (localPackageCollection, _) =>
        new ServiceStartHandler(localPackageCollection, packageRunner)
    }

    new Cosmos(
      new CapabilitiesHandler,
      packageAddHandler,
      new PackageDescribeHandler(repositories),
      new PackageInstallHandler(repositories, packageRunner),
      new ListHandler(adminRouter, uri => repositories.getRepository(uri)),
      new ListVersionsHandler(repositories),
      new PackageRenderHandler(repositories),
      new PackageRepositoryAddHandler(sourcesStorage),
      new PackageRepositoryDeleteHandler(sourcesStorage),
      new PackageRepositoryListHandler(sourcesStorage),
      new PackageSearchHandler(repositories),
      new UninstallHandler(adminRouter, repositories),
      serviceStartHandler
    )(statsReceiver)
  }

  private[this] def enableIfSome[A, Req, Res](requirement: Option[A], operationName: String)(
    f: A => EndpointHandler[Req, Res]
  ): EndpointHandler[Req, Res] = {
    requirement.fold[EndpointHandler[Req, Res]](new NotConfiguredHandler(operationName))(f)
  }
}
