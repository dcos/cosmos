package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.finch._
import _root_.io.finch.circe.dropNullKeys._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.finch.FinchExtensions._
import com.mesosphere.cosmos.finch.RequestError
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
import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.storage.installqueue.InstallQueue
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.app.App
import com.twitter.app.Flag
import com.twitter.conversions.storage.intToStorageUnitableWholeNumber
import com.twitter.finagle.Http
import com.twitter.finagle.ListeningServer
import com.twitter.finagle.Service
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.finagle.param.Label
import com.twitter.finagle.ssl.Ssl
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.Buf
import com.twitter.server.Admin
import com.twitter.server.AdminHttpServer
import com.twitter.server.Lifecycle
import com.twitter.server.Stats
import com.twitter.util.Await
import com.twitter.util.Try
import java.net.InetSocketAddress
import java.util.logging.Level
import java.util.logging.LogManager
import org.slf4j.Logger
import org.slf4j.bridge.SLF4JBridgeHandler


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
    route(post("package" / "install"), packageInstallHandler)(RequestValidators.standard)
  }

  val packageUninstall: Endpoint[Json] = {
    route(post("package" / "uninstall"), packageUninstallHandler)(RequestValidators.standard)
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
    route(post("package" / "list"), packageListHandler)(RequestValidators.standard)
  }

  val packageRepositoryList: Endpoint[Json] = {
    route(post("package" / "repository" / "list"), packageRepositoryListHandler)(RequestValidators.standard)
  }

  val packageRepositoryAdd: Endpoint[Json] = {
    route(post("package" / "repository" / "add"), packageRepositoryAddHandler)(RequestValidators.standard)
  }

  val packageRepositoryDelete: Endpoint[Json] = {
    route(post("package" / "repository" / "delete"), packageRepositoryDeleteHandler)(RequestValidators.standard)
  }

  val packageAdd: Endpoint[Json] = {
    route(post("package" / "add"), packageAddHandler)(RequestValidators.selectedBody)
  }

  // Service Handlers
  val serviceStart: Endpoint[Json] = {
    route(post("service" / "start"), serviceStartHandler)(RequestValidators.standard)
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
      case re: RequestError =>
        stats.counter(s"definedError/${sanitiseClassName(re.getClass)}").incr()
        val output = Output.failure(
          re,
          re.status
        ).withHeader(
          "Content-Type" -> MediaTypes.ErrorResponse.show
        )
        re.getHeaders.foldLeft(output) { case (out, kv) => out.withHeader(kv) }
      case fe: _root_.io.finch.Error =>
        stats.counter(s"finchError/${sanitiseClassName(fe.getClass)}").incr()
        Output.failure(
          fe,
          Status.BadRequest
        ).withHeader(
          "Content-Type" -> MediaTypes.ErrorResponse.show
        )
      case e: Exception if !e.isInstanceOf[_root_.io.finch.Error] =>
        stats.counter(s"unhandledException/${sanitiseClassName(e.getClass)}").incr()
        logger.warn("Unhandled exception: ", e)
        Output.failure(
          e,
          Status.InternalServerError
        ).withHeader(
          "Content-Type" -> MediaTypes.ErrorResponse.show
        )
      case t: Throwable if !t.isInstanceOf[_root_.io.finch.Error] =>
        stats.counter(s"unhandledThrowable/${sanitiseClassName(t.getClass)}").incr()
        logger.warn("Unhandled throwable: ", t)
        Output.failure(
          new Exception(t),
          Status.InternalServerError
        ).withHeader(
          "Content-Type" -> MediaTypes.ErrorResponse.show
        )
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

// TODO: Deal with access logs
// TODO: Deal with TLS
// TODO: This object is doing too much. This should be a simple def main with most of logic moved to the class above!!!
object Cosmos
extends App
with AdminHttpServer
with Admin
with Lifecycle
with Lifecycle.Warmup
with Stats {

  lazy val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def main(): Unit = {
    for (httpServer <- startServer) {
      logger.info(s"HTTP server started on ${httpServer.boundAddress}")
      closeOnExit(httpServer)
      Await.result(httpServer)
    }

    // TODO: Start HTTPS server
  }

  private[this] def startServer(): Option[ListeningServer] = {
    // TODO: Make default http port configurable
    Some(new InetSocketAddress("0.0.0.0", 7070)).map { iface => // scalastyle:ignore magic.number
      //.configured(Label(name)) TODO: Add this back when we find out what it is doing.
      //.configured(Http.param.MaxRequestSize(config.maxRequestSize.megabytes)) TODO: Add this back

      Http
        .server
        .serve(iface, startCosmos(NullStatsReceiver)) // TODO: User a better stats receiver
    }
  }

  /*
  private[this] def startTlsServer(): Option[ListeningServer] = {
    // TODO: Make default https port configurable
    Option.empty[(String, String, InetSocketAddress)].map {
      case (certificatePath, keyPath, iface) =>
        val engineFactory = () =>
          Ssl.server(certificatePath, keyPath, null, null, null) // scalastyle:ignore null

        //.configured(Label(name)) TODO: Add this back when we find out what it is doing
        //.configured(Http.param.MaxRequestSize(config.maxRequestSize.megabytes)) TODO: Add this back

        Http
          .server
          .withTls(Netty3ListenerTLSConfig(engineFactory))
          .serve(iface, startCosmos(NullStatsReceiver)) // TODO: User a better stats receiver
    }
  }
  */

  private[this] def startCosmos(implicit stats: StatsReceiver): Service[Request, Response] = {
    HttpProxySupport.configureProxySupport()

    val adminRouter = configureDcosClients().get

    val zkUri = zookeeperUri()
    logger.info("Using {} for the ZooKeeper connection", zkUri)

    val objectStorages = configureObjectStorage()

    val zkClient = zookeeper.Clients.createAndInitialize(zkUri)
    onExit(zkClient.close())

    val repoList = ZkRepositoryList(zkClient)
    onExit(repoList.close())

    val installQueue = InstallQueue(zkClient)
    onExit(installQueue.close())

    for ((pkgStorage, stageStorage, localPackageCollection) <- objectStorages) {
      val processingLeader = SyncFutureLeader(
        zkClient,
        OperationProcessor(
          installQueue,
          DefaultInstaller(stageStorage, pkgStorage, localPackageCollection),
          DefaultUniverseInstaller(pkgStorage, localPackageCollection),
          Uninstaller.Noop
        )
      )
      onExit(processingLeader.close())
    }

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

  // TODO: Logging Configuration. Move this out of here.
  init {
    // Turn off Java util logging so that slf4j can configure it
    LogManager.getLogManager.getLogger("").getHandlers.toList.foreach { l =>
      l.setLevel(Level.OFF)
    }
    org.slf4j.LoggerFactory.getLogger("slf4j-logging").debug("Installing SLF4JLogging")
    SLF4JBridgeHandler.install()
  }

  onExit {
    org.slf4j.LoggerFactory.getLogger("slf4j-logging").debug("Uninstalling SLF4JLogging")
    SLF4JBridgeHandler.uninstall()
  }
}
