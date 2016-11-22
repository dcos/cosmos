package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.finch._
import _root_.io.finch.circe.dropNullKeys._
import _root_.io.github.benwhitehead.finch.FinchServer
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.finch.FinchExtensions._
import com.mesosphere.cosmos.finch.RequestError
import com.mesosphere.cosmos.finch.RequestValidators
import com.mesosphere.cosmos.handler._
import com.mesosphere.cosmos.repository.DefaultInstaller
import com.mesosphere.cosmos.repository.LocalPackageCollection
import com.mesosphere.cosmos.repository.OperationProcessor
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.repository.SyncFutureLeader
import com.mesosphere.cosmos.repository.Uninstaller
import com.mesosphere.cosmos.repository.UniverseClient
import com.mesosphere.cosmos.repository.UniverseInstaller
import com.mesosphere.cosmos.repository.ZkRepositoryList
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedEncoders._
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedRequestDecoders._
import com.mesosphere.cosmos.rpc.v2.circe.MediaTypedEncoders._
import com.mesosphere.cosmos.storage.InMemoryPackageStorage
import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.storage.installqueue.InstallQueue
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.app.Flag
import com.twitter.conversions.storage.intToStorageUnitableWholeNumber
import com.twitter.finagle.Http
import com.twitter.finagle.ListeningServer
import com.twitter.finagle.Service
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.finagle.netty3.Netty3ListenerTLSConfig
import com.twitter.finagle.param.Label
import com.twitter.finagle.ssl.Ssl
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Try
import org.slf4j.Logger

private[cosmos] final class Cosmos(
  uninstallHandler: EndpointHandler[rpc.v1.model.UninstallRequest, rpc.v1.model.UninstallResponse],
  packageInstallHandler: EndpointHandler[rpc.v1.model.InstallRequest, rpc.v2.model.InstallResponse],
  packageRenderHandler: EndpointHandler[rpc.v1.model.RenderRequest, rpc.v1.model.RenderResponse],
  packageSearchHandler: EndpointHandler[rpc.v1.model.SearchRequest, rpc.v1.model.SearchResponse],
  packageDescribeHandler: EndpointHandler[rpc.v1.model.DescribeRequest, universe.v3.model.PackageDefinition],
  packageListVersionsHandler: EndpointHandler[rpc.v1.model.ListVersionsRequest, rpc.v1.model.ListVersionsResponse],
  listHandler: EndpointHandler[rpc.v1.model.ListRequest, rpc.v1.model.ListResponse],
  listRepositoryHandler: EndpointHandler[rpc.v1.model.PackageRepositoryListRequest, rpc.v1.model.PackageRepositoryListResponse],
  addRepositoryHandler: EndpointHandler[rpc.v1.model.PackageRepositoryAddRequest, rpc.v1.model.PackageRepositoryAddResponse],
  deleteRepositoryHandler: EndpointHandler[rpc.v1.model.PackageRepositoryDeleteRequest, rpc.v1.model.PackageRepositoryDeleteResponse],
  packagePublishHandler: EndpointHandler[rpc.v1.model.PublishRequest, rpc.v1.model.PublishResponse],
  repositoryServeHandler: EndpointHandler[Unit, universe.v3.model.Repository],
  capabilitiesHandler: CapabilitiesHandler,
  packageAddHandler: EndpointHandler[rpc.v1.model.AddRequest, rpc.v1.model.AddResponse],
  serviceStartHandler: EndpointHandler[rpc.v1.model.ServiceStartRequest, rpc.v1.model.ServiceStartResponse]
)(implicit statsReceiver: StatsReceiver = NullStatsReceiver) {

  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(classOf[Cosmos])

  val packageInstall: Endpoint[Json] = {
    route(post("package" / "install"), packageInstallHandler)(RequestValidators.standard)
  }

  val packageUninstall: Endpoint[Json] = {
    route(post("package" / "uninstall"), uninstallHandler)(RequestValidators.standard)
  }

  val packageDescribe: Endpoint[Json] = {
    route(post("package" / "describe"), packageDescribeHandler)(RequestValidators.standard)
  }

  val packageRender: Endpoint[Json] = {
    route(post("package" / "render"), packageRenderHandler)(RequestValidators.standard)
  }

  val packageListVersions: Endpoint[Json] = {
    route(post("package" / "list-versions"), packageListVersionsHandler)(RequestValidators.standard)
  }

  val packageSearch: Endpoint[Json] = {
    route(post("package" / "search"), packageSearchHandler)(RequestValidators.standard)
  }

  val packageList: Endpoint[Json] = {
    route(post("package" / "list"), listHandler)(RequestValidators.standard)
  }

  val capabilities: Endpoint[Json] = {
    route(get("capabilities"), capabilitiesHandler)(RequestValidators.noBody)
  }

  val packageListSources: Endpoint[Json] = {
    route(post("package" / "repository" / "list"), listRepositoryHandler)(RequestValidators.standard)
  }

  val packageAddSource: Endpoint[Json] = {
    route(post("package" / "repository" / "add"), addRepositoryHandler)(RequestValidators.standard)
  }

  val packageDeleteSource: Endpoint[Json] = {
    route(post("package" / "repository" / "delete"), deleteRepositoryHandler)(RequestValidators.standard)
  }

  val packagePublish: Endpoint[Json] = {
    route(post("package" / "publish"), packagePublishHandler)(RequestValidators.standard)
  }

  val repositoryServe: Endpoint[Json] = {
    route(get("package" / "storage" / "repository"), repositoryServeHandler)(RequestValidators.noBody)
  }

  val packageAdd: Endpoint[Json] = {
    route(post("package" / "add"), packageAddHandler)(RequestValidators.streamed(rpc.v1.model.AddRequest(_, _)))
  }

  val service: Service[Request, Response] = {
    val stats = statsReceiver.scope("errorFilter")

    val endpoints = (
      // Keep alphabetized
      capabilities
        :+: packageAdd
        :+: packageAddSource
        :+: packageDeleteSource
        :+: packageDescribe
        :+: packageInstall
        :+: packageList
        :+: packageListSources
        :+: packageListVersions
        :+: packagePublish
        :+: packageRender
        :+: packageSearch
        :+: packageUninstall
        :+: repositoryServe
      )

    val api = endpoints.handle {
      case re: RequestError =>
        stats.counter(s"definedError/${sanitiseClassName(re.getClass)}").incr()
        val output = Output.failure(re, re.status).withContentType(Some(MediaTypes.ErrorResponse.show))
        re.getHeaders.foldLeft(output) { case (out, kv) => out.withHeader(kv) }
      case fe: _root_.io.finch.Error =>
        stats.counter(s"finchError/${sanitiseClassName(fe.getClass)}").incr()
        Output.failure(fe, Status.BadRequest).withContentType(Some(MediaTypes.ErrorResponse.show))
      case e: Exception if !e.isInstanceOf[_root_.io.finch.Error] =>
        stats.counter(s"unhandledException/${sanitiseClassName(e.getClass)}").incr()
        logger.warn("Unhandled exception: ", e)
        Output.failure(e, Status.InternalServerError).withContentType(Some(MediaTypes.ErrorResponse.show))
      case t: Throwable if !t.isInstanceOf[_root_.io.finch.Error] =>
        stats.counter(s"unhandledThrowable/${sanitiseClassName(t.getClass)}").incr()
        logger.warn("Unhandled throwable: ", t)
        Output.failure(new Exception(t), Status.InternalServerError).withContentType(Some(MediaTypes.ErrorResponse.show))
    }

    api.toService
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

object Cosmos extends FinchServer {
  def service: Service[Request, Response] = {
    HttpProxySupport.configureProxySupport()

    startCosmos(statsReceiver.scope("cosmos"))
  }

  override def startServer(): Option[ListeningServer] = {
    config.httpInterface.map { iface =>
      val name = s"http/$serverName"
      Http.server
        .configured(Label(name))
        .configured(Http.param.MaxRequestSize(config.maxRequestSize.megabytes))
        .withStreaming(enabled = true)
        .serve(iface, getService(s"srv/$name"))
    }
  }

  // TODO package-add: is this being tested?
  override def startTlsServer(): Option[ListeningServer] = {
    config.httpsInterface.map { iface =>
      val name = s"https/$serverName"
      val engineFactory = () =>
        Ssl.server(config.certificatePath, config.keyPath, null, null, null) // scalastyle:ignore null
      Http.server
        .configured(Label(name))
        .configured(Http.param.MaxRequestSize(config.maxRequestSize.megabytes))
        .withStreaming(enabled = true)
        .withTls(Netty3ListenerTLSConfig(engineFactory))
        .serve(iface, getService(s"srv/$name"))
    }
  }

  private[this] def startCosmos(implicit stats: StatsReceiver): Service[Request, Response] = {
    val adminRouter = configureDcosClients().get

    val zkUri = zookeeperUri()
    logger.info("Using {} for the ZooKeeper connection", zkUri)

    val objectStorages = configureObjectStorage()

    val zkClient = zookeeper.Clients.createAndInitialize(zkUri)
    onExit(zkClient.close())

    val installQueue = InstallQueue(zkClient)
    onExit(installQueue.close())

    for ((pkgStorage, stageStorage, localPackageCollection) <- objectStorages) {
      val processingLeader = SyncFutureLeader(
        zkClient,
        OperationProcessor(
          installQueue,
          DefaultInstaller(
            stageStorage,
            pkgStorage,
            localPackageCollection
          ),
          UniverseInstaller.Noop,
          Uninstaller.Noop
        )
      )
      onExit(processingLeader.close())
    }

    Cosmos(
      adminRouter,
      new MarathonPackageRunner(adminRouter),
      new ZkRepositoryList(zkClient),
      UniverseClient(adminRouter),
      installQueue,
      objectStorages.map {
        case (_, stagedStorage, localCollection) =>
          (localCollection, stagedStorage)
      }
    ).service
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
          logger.info("Connection to AdminRouter at: {}", adminRouter)
          (adminRouter, mar, master)
      }
      .flatMap { case (adminRouterUri, marathonUri, mesosMasterUri) =>
        Trys.join(
          Services.adminRouterClient(adminRouterUri).map { adminRouterUri -> _ },
          Services.marathonClient(marathonUri).map { marathonUri -> _ },
          Services.mesosClient(mesosMasterUri).map { mesosMasterUri -> _ }
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
  )(
    implicit statsReceiver: StatsReceiver
  ): Option[(PackageObjectStorage, StagedPackageStorage, LocalPackageCollection)] = {
    def fromFlag(
      flag: Flag[Option[ObjectStorageUri]],
      description: String
    ): Option[ObjectStorage] = {
      val value = flag().map(_.toString).getOrElse("None")
      logger.info(s"Using {} for the $description storage URI", value)
      flag().map(uri => ObjectStorage.fromUri(uri)(statsReceiver))
    }

    (
      fromFlag(packageStorageUri, "package").map(PackageObjectStorage(_)),
      fromFlag(stagedPackageStorageUri, "staged package").map(StagedPackageStorage(_))
    ) match {
      case (Some(pkgStorage), Some(stagedStorage)) =>
        Some((pkgStorage, stagedStorage, LocalPackageCollection(pkgStorage)))
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
      case (_, stagedStorage) => new PackageAddHandler(stagedStorage, installQueue)
    }

    val serviceStartHandler = enableIfSome(objectStorages, "service start") {
      case (localPackageCollection, _) =>
        new ServiceStartHandler(localPackageCollection, packageRunner)
    }

    val packageStorage = new InMemoryPackageStorage()

    new Cosmos(
      new UninstallHandler(adminRouter, repositories),
      new PackageInstallHandler(repositories, packageRunner),
      new PackageRenderHandler(repositories),
      new PackageSearchHandler(repositories),
      new PackageDescribeHandler(repositories),
      new ListVersionsHandler(repositories),
      new ListHandler(adminRouter, uri => repositories.getRepository(uri)),
      new PackageRepositoryListHandler(sourcesStorage),
      new PackageRepositoryAddHandler(sourcesStorage),
      new PackageRepositoryDeleteHandler(sourcesStorage),
      new PackagePublishHandler(packageStorage),
      new RepositoryServeHandler(packageStorage),
      new CapabilitiesHandler,
      packageAddHandler,
      serviceStartHandler
    )(statsReceiver)
  }

  private[this] def enableIfSome[A, Req, Res](requirement: Option[A], operationName: String)(
    f: A => EndpointHandler[Req, Res]
  ): EndpointHandler[Req, Res] = {
    requirement.fold[EndpointHandler[Req, Res]](new NotConfiguredHandler(operationName))(f)
  }

}
