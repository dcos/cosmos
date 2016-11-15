package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.finch._
import _root_.io.finch.circe.dropNullKeys._
import _root_.io.github.benwhitehead.finch.FinchServer
import com.amazonaws.services.s3.AmazonS3Client
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.finch.FinchExtensions._
import com.mesosphere.cosmos.finch.RequestError
import com.mesosphere.cosmos.finch.RequestValidators
import com.mesosphere.cosmos.handler._
import com.mesosphere.cosmos.repository.LocalPackageCollection
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.repository.UniverseClient
import com.mesosphere.cosmos.repository.ZkRepositoryList
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedEncoders._
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedRequestDecoders._
import com.mesosphere.cosmos.rpc.v2.circe.MediaTypedEncoders._
import com.mesosphere.cosmos.storage._
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.finagle.Service
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Try
import shapeless.HNil

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
  repositoryServeHandler: RepositoryServeHandler,
  capabilitiesHandler: CapabilitiesHandler,
  serviceStartHandler: EndpointHandler[rpc.v1.model.ServiceStartRequest, rpc.v1.model.ServiceStartResponse]
)(implicit statsReceiver: StatsReceiver = NullStatsReceiver) {

  lazy val logger = org.slf4j.LoggerFactory.getLogger(classOf[Cosmos])

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

  val service: Service[Request, Response] = {
    val stats = statsReceiver.scope("errorFilter")

    (packageInstall
      :+: packageRender
      :+: packageDescribe
      :+: packageSearch
      :+: packageUninstall
      :+: packageListVersions
      :+: packageList
      :+: packageListSources
      :+: packageAddSource
      :+: packageDeleteSource
      :+: capabilities
      :+: packagePublish
      :+: repositoryServe
    )
      .handle {
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
      .toService
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
    implicit val stats = statsReceiver.scope("cosmos")

    HttpProxySupport.configureProxySupport()

    val boot = configureDcosClients() map { adminRouter =>
      val zkUri = zookeeperUri()
      logger.info("Using {} for the ZooKeeper connection", zkUri)

      logger.info("Using {} for the staging uri", stagedPackageUri())
      val objectStorage = stagedPackageUri() match {
        case Some(S3Uri(uri)) => Some(S3ObjectStorage(new AmazonS3Client(), uri))
        case Some(FileUri(path)) => Some(LocalObjectStorage(path))
        case None => None
      }

      val marathonPackageRunner = new MarathonPackageRunner(adminRouter)

      val zkClient = zookeeper.Clients.createAndInitialize(
        zkUri,
        sys.env.get("ZOOKEEPER_USER").zip(sys.env.get("ZOOKEEPER_SECRET")).headOption
      )
      onExit {
        zkClient.close()
      }

      val sourcesStorage = new ZkRepositoryList(zkClient)()
      val packageStorage = new InMemoryPackageStorage()

      val cosmos =
        Cosmos(
          adminRouter,
          marathonPackageRunner,
          sourcesStorage,
          packageStorage,
          UniverseClient(adminRouter),
          objectStorage
        )
      cosmos.service
    }
    boot.get
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

  private[cosmos] def apply(
    adminRouter: AdminRouter,
    packageRunner: PackageRunner,
    sourcesStorage: PackageSourcesStorage,
    packageStorage: PackageStorage,
    universeClient: UniverseClient,
    objectStorage: Option[ObjectStorage]
  )(implicit statsReceiver: StatsReceiver = NullStatsReceiver): Cosmos = {

    val repositories = new MultiRepository(
      sourcesStorage,
      universeClient
    )

    val serviceStartHandler = objectStorage match {
      case Some(objStore) =>
        new ServiceStartHandler(LocalPackageCollection(PackageObjectStorage(objStore)), packageRunner)
      case None =>
        new ServiceStartNotConfiguredHandler
    }

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
      serviceStartHandler
    )(statsReceiver)
  }

}
