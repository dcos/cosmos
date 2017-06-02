package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.finch._
import _root_.io.finch.circe.dropNullKeys._
import com.mesosphere.cosmos.app.Logging
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.finch.DispatchingMediaTypedEncoder
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.finch.FinchExtensions._
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder
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
import com.mesosphere.cosmos.handler.ServiceDescribeHandler
import com.mesosphere.cosmos.handler.ServiceStartHandler
import com.mesosphere.cosmos.handler.UninstallHandler
import com.mesosphere.cosmos.janitor.Janitor
import com.mesosphere.cosmos.janitor.SdkJanitor
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
import com.mesosphere.cosmos.storage.installqueue.ProcessorView
import com.mesosphere.cosmos.storage.installqueue.ProducerView
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
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.server.Admin
import com.twitter.server.AdminHttpServer
import com.twitter.server.Lifecycle
import com.twitter.server.Stats
import com.twitter.util.Await
import com.twitter.util.Try
import org.apache.curator.framework.CuratorFramework
import org.slf4j.Logger
import shapeless.:+:
import shapeless.CNil
import shapeless.Coproduct
import shapeless.HNil
import shapeless.Inl
import shapeless.Inr
import scala.concurrent.duration._
import scala.language.implicitConversions

trait CosmosApp
extends App
with AdminHttpServer
with Admin
with Lifecycle
with Lifecycle.Warmup
with Stats
with Logging {

  import CosmosApp._

  def main(): Unit

  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  protected final def buildComponents(): Components = {
    implicit val sr = statsReceiver

    val adminRouter = configureDcosClients().get
    val packageRunner = new MarathonPackageRunner(adminRouter)

    val zkUri = zookeeperUri()
    logger.info("Using {} for the ZooKeeper connection", zkUri)

    val zkClient = zookeeper.Clients.createAndInitialize(zkUri)
    onExit(zkClient.close())

    val janitor = SdkJanitor.initializeJanitor(zkClient, adminRouter)
    janitor.start()
    onExit(janitor.close())

    val sourcesStorage = ZkRepositoryList(zkClient)
    onExit(sourcesStorage.close())

    val installQueue = InstallQueue(zkClient)
    onExit(installQueue.close())

    val objectStorages = configureObjectStorage(zkClient, installQueue)

    val universeClient = UniverseClient(adminRouter)

    val repositories = new MultiRepository(
      sourcesStorage,
      universeClient
    )

    new Components(
      adminRouter,
      sourcesStorage,
      objectStorages,
      repositories,
      installQueue,
      packageRunner,
      janitor
    )
  }

  final def buildHandlers(components: Components): Handlers = {
    import components._

    val packageAddHandler = enableIfSome(objectStorages, "package add") {
      case (_, stagedStorage) => new PackageAddHandler(repositories, stagedStorage, producerView)
    }

    val serviceStartHandler = enableIfSome(objectStorages, "service start") {
      case (localPackageCollection, _) =>
        new ServiceStartHandler(localPackageCollection, packageRunner)
    }

    new Handlers(
      // Keep alphabetized
      capabilities = new CapabilitiesHandler,
      packageAdd = packageAddHandler,
      packageDescribe = new PackageDescribeHandler(repositories),
      packageInstall = new PackageInstallHandler(repositories, packageRunner),
      packageList = new ListHandler(adminRouter, uri => repositories.getRepository(uri)),
      packageListVersions = new ListVersionsHandler(repositories),
      packageRender = new PackageRenderHandler(repositories),
      packageRepositoryAdd = new PackageRepositoryAddHandler(sourcesStorage),
      packageRepositoryDelete = new PackageRepositoryDeleteHandler(sourcesStorage),
      packageRepositoryList = new PackageRepositoryListHandler(sourcesStorage),
      packageSearch = new PackageSearchHandler(repositories),
      packageUninstall = new UninstallHandler(adminRouter, repositories, marathonSdkJanitor),
      serviceDescribe = new ServiceDescribeHandler(adminRouter, repositories),
      serviceStart = serviceStartHandler
    )
  }

  final def buildEndpoints(handlers: Handlers): Endpoints = {
    import handlers._

    val pkg = "package"
    val repo = "repository"

    Endpoints(
      // Keep alphabetized
      capabilities = route(get("capabilities"), capabilities)(RequestValidators.noBody),
      packageAdd = route(post(pkg :: "add"), packageAdd)(RequestValidators.selectedBody),
      packageDescribe = standardEndpoint(pkg :: "describe", packageDescribe),
      packageInstall = standardEndpoint(pkg :: "install", packageInstall),
      packageList = standardEndpoint(pkg :: "list", packageList),
      packageListVersions = standardEndpoint(pkg :: "list-versions", packageListVersions),
      packageRender = standardEndpoint(pkg :: "render", packageRender),
      packageRepositoryAdd = standardEndpoint(pkg :: repo :: "add", packageRepositoryAdd),
      packageRepositoryDelete = standardEndpoint(pkg :: repo :: "delete", packageRepositoryDelete),
      packageRepositoryList = standardEndpoint(pkg :: repo :: "list", packageRepositoryList),
      packageSearch = standardEndpoint(pkg :: "search", packageSearch),
      packageUninstall = standardEndpoint(pkg :: "uninstall", packageUninstall),
      serviceDescribe = standardEndpoint("service" :: "describe", serviceDescribe),
      serviceStart = standardEndpoint("service" :: "start", serviceStart)
    )
  }

  protected final def start(allEndpoints: Endpoint[Json]): Unit = {
    HttpProxySupport.configureProxySupport()
    implicit val sr = statsReceiver

    val service = LoggingFilter.andThen(buildService(allEndpoints))
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

  private[this] def configureObjectStorage(
    zkClient: CuratorFramework,
    installQueue: ProcessorView with ReaderView
  )(
    implicit statsReceiver: StatsReceiver
  ): Option[(LocalPackageCollection, StagedPackageStorage)] = {
    def fromFlag(
      flag: Flag[ObjectStorageUri],
      description: String
    ): Option[ObjectStorage] = {
      val value = flag.get.map(_.toString).getOrElse("None")
      logger.info(s"Using {} for the $description storage URI", value)
      flag.get.map(uri => ObjectStorage.fromUri(uri)(statsReceiver))
    }

    val validObjectStorages = (
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

    for ((packageStorage, stageStorage, _) <- validObjectStorages) {
      configureBackgroundTasks(zkClient, installQueue, packageStorage, stageStorage)
    }

    validObjectStorages.map {
      case (_, stagedStorage, localCollection) => (localCollection, stagedStorage)
    }
  }

  private[this] def configureBackgroundTasks(
    zkClient: CuratorFramework,
    installQueue: ProcessorView with ReaderView,
    packageStorage: PackageStorage,
    stageStorage: StagedPackageStorage
  ): Unit = {
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

  private[this] def buildService(endpoints: Endpoint[Json])(implicit
    statsReceiver: StatsReceiver
  ): Service[Request, Response] = {
    val stats = statsReceiver.scope("errorFilter")

    val api = endpoints.handle {
      case ce: CosmosException =>
        stats.counter(s"definedError/${sanitizeClassName(ce.error.getClass)}").incr()
        val output = Output.failure(
          ce,
          ce.status
        ).withHeader(
          Fields.ContentType -> MediaTypes.ErrorResponse.show
        )
        ce.headers.foldLeft(output) { case (out, kv) => out.withHeader(kv) }
      case fe @ (_: _root_.io.finch.Error | _: _root_.io.finch.Errors) =>
        stats.counter(s"finchError/${sanitizeClassName(fe.getClass)}").incr()
        Output.failure(
          fe.asInstanceOf[Exception], // Must be an Exception based on the types
          Status.BadRequest
        ).withHeader(
          Fields.ContentType -> MediaTypes.ErrorResponse.show
        )
      case e: Exception =>
        stats.counter(s"unhandledException/${sanitizeClassName(e.getClass)}").incr()
        logger.warn("Unhandled exception: ", e)
        Output.failure(
          e,
          Status.InternalServerError
        ).withHeader(
          Fields.ContentType -> MediaTypes.ErrorResponse.show
        )
      case t: Throwable =>
        stats.counter(s"unhandledThrowable/${sanitizeClassName(t.getClass)}").incr()
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

}

object CosmosApp {

  final class Components(
    val adminRouter: AdminRouter,
    val sourcesStorage: PackageSourcesStorage,
    val objectStorages: Option[(LocalPackageCollection, StagedPackageStorage)],
    val repositories: MultiRepository,
    val producerView: ProducerView,
    val packageRunner: PackageRunner,
    val marathonSdkJanitor: Janitor
  )

  final class Handlers(
    // Keep alphabetized
    val capabilities: EndpointHandler[Unit, rpc.v1.model.CapabilitiesResponse],
    val packageAdd: EndpointHandler[rpc.v1.model.AddRequest, rpc.v1.model.AddResponse],
    val packageDescribe: EndpointHandler[rpc.v1.model.DescribeRequest, universe.v4.model.PackageDefinition],
    val packageInstall: EndpointHandler[rpc.v1.model.InstallRequest, rpc.v2.model.InstallResponse],
    val packageList: EndpointHandler[rpc.v1.model.ListRequest, rpc.v1.model.ListResponse],
    val packageListVersions: EndpointHandler[rpc.v1.model.ListVersionsRequest, rpc.v1.model.ListVersionsResponse],
    val packageRender: EndpointHandler[rpc.v1.model.RenderRequest, rpc.v1.model.RenderResponse],
    val packageRepositoryAdd: EndpointHandler[rpc.v1.model.PackageRepositoryAddRequest, rpc.v1.model.PackageRepositoryAddResponse],
    val packageRepositoryDelete: EndpointHandler[rpc.v1.model.PackageRepositoryDeleteRequest, rpc.v1.model.PackageRepositoryDeleteResponse],
    val packageRepositoryList: EndpointHandler[rpc.v1.model.PackageRepositoryListRequest, rpc.v1.model.PackageRepositoryListResponse],
    val packageSearch: EndpointHandler[rpc.v1.model.SearchRequest, rpc.v1.model.SearchResponse],
    val packageUninstall: EndpointHandler[rpc.v1.model.UninstallRequest, rpc.v1.model.UninstallResponse],
    val serviceDescribe: EndpointHandler[rpc.v1.model.ServiceDescribeRequest, rpc.v1.model.ServiceDescribeResponse],
    val serviceStart: EndpointHandler[rpc.v1.model.ServiceStartRequest, rpc.v1.model.ServiceStartResponse]
  )

  final case class Endpoints(
    // Keep alphabetized
    capabilities: Endpoint[Json],
    packageAdd: Endpoint[Json],
    packageDescribe: Endpoint[Json],
    packageInstall: Endpoint[Json],
    packageList: Endpoint[Json],
    packageListVersions: Endpoint[Json],
    packageRender: Endpoint[Json],
    packageRepositoryAdd: Endpoint[Json],
    packageRepositoryDelete: Endpoint[Json],
    packageRepositoryList: Endpoint[Json],
    packageSearch: Endpoint[Json],
    packageUninstall: Endpoint[Json],
    serviceDescribe: Endpoint[Json],
    serviceStart: Endpoint[Json]
  ) {

    def combine: Endpoint[Json] = {
      // Keep alphabetized
      (capabilities
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
        :+: serviceDescribe
        :+: serviceStart).map(degenerateCoproduct)
    }

  }

  private def enableIfSome[A, Req, Res](requirement: Option[A], operationName: String)(
    f: A => EndpointHandler[Req, Res]
  ): EndpointHandler[Req, Res] = {
    requirement.fold[EndpointHandler[Req, Res]](new NotConfiguredHandler(operationName))(f)
  }

  def standardEndpoint[Req, Res](
    path: Endpoint[HNil],
    handler: EndpointHandler[Req, Res]
  )(implicit
    accepts: MediaTypedRequestDecoder[Req],
    produces: DispatchingMediaTypedEncoder[Res]
  ): Endpoint[Json] = {
    route(post(path), handler)(RequestValidators.standard)
  }

  private def startServer(service: Service[Request, Response]): Option[ListeningServer] = {
    getHttpInterface.map { interface =>
      Http
        .server
        .configured(Label("http"))
        .serve(interface, service)
    }
  }

  private def startTlsServer(
    service: Service[Request, Response]
  ): Option[ListeningServer] = {
    getHttpsInterface.map { interface =>
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
        .serve(interface, service)
    }
  }

  /**
   * Removes characters from class names that are disallowed by some metrics systems.
   *
   * @param clazz the class whose name is to be sanitized
   * @return The name of the specified class with all "illegal characters" replaced with '.'
   */
  private def sanitizeClassName(clazz: Class[_]): String = {
    clazz.getName.replaceAllLiterally("$", ".")
  }

  implicit def degenerateCNil[A](cnil: CNil): A = cnil.impossible

  implicit def degenerateCoproduct[H, T <: Coproduct](implicit toH: T => H): (H :+: T => H) = {
    case Inl(h) => h
    case Inr(t) => toH(t)
  }

}

object Cosmos extends CosmosApp {

  override def main(): Unit = {
    val components = buildComponents()
    val handlers = buildHandlers(components)
    val endpoints = buildEndpoints(handlers)
    start(endpoints.combine)
  }

}
