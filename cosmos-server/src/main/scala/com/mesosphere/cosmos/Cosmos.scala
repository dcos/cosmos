package com.mesosphere.cosmos

import _root_.io.finch._
import _root_.io.finch.circe.dropNullKeys._
import _root_.io.finch.internal.ToResponse
import com.mesosphere.cosmos.app.Logging
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.repository.DefaultInstaller
import com.mesosphere.cosmos.repository.DefaultUniverseInstaller
import com.mesosphere.cosmos.repository.LocalPackageCollection
import com.mesosphere.cosmos.repository.OperationProcessor
import com.mesosphere.cosmos.repository.SyncFutureLeader
import com.mesosphere.cosmos.repository.Uninstaller
import com.mesosphere.cosmos.repository.UniverseClient
import com.mesosphere.cosmos.repository.ZkRepositoryList
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.storage.GarbageCollector
import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.cosmos.storage.PackageStorage
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.storage.installqueue.InstallQueue
import com.mesosphere.cosmos.storage.installqueue.ProcessorView
import com.mesosphere.cosmos.storage.installqueue.ReaderView
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
import org.apache.curator.framework.CuratorFramework
import org.slf4j.Logger
import scala.concurrent.duration._

final class Cosmos[A](allEndpoints: Endpoint[A])(implicit
  statsReceiver: StatsReceiver = NullStatsReceiver,
  tr: ToResponse.Aux[A, Application.Json],
  tre: ToResponse.Aux[Exception, Application.Json]
) {

  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(classOf[Cosmos[_]])

  val service: Service[Request, Response] = {
    val stats = statsReceiver.scope("errorFilter")

    val api = allEndpoints.handle {
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

trait CosmosApp
extends App
with AdminHttpServer
with Admin
with Lifecycle
with Lifecycle.Warmup
with Stats
with Logging {

  def main(): Unit

  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  final def buildApi(): CosmosApi = {
    implicit val sr = statsReceiver

    val adminRouter = configureDcosClients().get
    val packageRunner = new MarathonPackageRunner(adminRouter)

    val zkUri = zookeeperUri()
    logger.info("Using {} for the ZooKeeper connection", zkUri)

    val zkClient = zookeeper.Clients.createAndInitialize(zkUri)
    onExit(zkClient.close())

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

    new CosmosApi(
      adminRouter,
      sourcesStorage,
      objectStorages,
      repositories,
      installQueue,
      packageRunner
    )
  }

  final def start[A](allEndpoints: Endpoint[A])(implicit
    tr: ToResponse.Aux[A, Application.Json],
    tre: ToResponse.Aux[Exception, Application.Json]
  ): Unit = {
    HttpProxySupport.configureProxySupport()
    implicit val sr = statsReceiver

    val service = LoggingFilter.andThen(new Cosmos(allEndpoints).service)
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

}

object Main extends CosmosApp {

  override def main(): Unit = {
    val api = buildApi()
    start(api.allEndpoints)
  }

}
