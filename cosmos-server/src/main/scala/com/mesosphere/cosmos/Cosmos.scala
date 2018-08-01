package com.mesosphere.cosmos

import _root_.io.circe.Json
import _root_.io.finch.ToResponse
import _root_.io.finch._
import _root_.io.finch.circe.dropNullValues._
import _root_.io.finch.syntax._
import com.mesosphere.cosmos.app.Logging
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.finch.DispatchingMediaTypedEncoder
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.finch.FinchExtensions._
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder
import com.mesosphere.cosmos.finch.RequestValidators
import com.mesosphere.cosmos.handler.CapabilitiesHandler
import com.mesosphere.cosmos.handler.ListHandler
import com.mesosphere.cosmos.handler.ListVersionsHandler
import com.mesosphere.cosmos.handler.PackageDescribeHandler
import com.mesosphere.cosmos.handler.PackageInstallHandler
import com.mesosphere.cosmos.handler.PackageRenderHandler
import com.mesosphere.cosmos.handler.PackageRepositoryAddHandler
import com.mesosphere.cosmos.handler.PackageRepositoryDeleteHandler
import com.mesosphere.cosmos.handler.PackageRepositoryListHandler
import com.mesosphere.cosmos.handler.PackageSearchHandler
import com.mesosphere.cosmos.handler.ResourceProxyHandler
import com.mesosphere.cosmos.handler.ServiceDescribeHandler
import com.mesosphere.cosmos.handler.ServiceUpdateHandler
import com.mesosphere.cosmos.handler.UninstallHandler
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.repository.RepositoryCache
import com.mesosphere.cosmos.repository.UniverseClient
import com.mesosphere.cosmos.repository.ZkRepositoryList
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v2.circe.MediaTypedEncoders._
import com.mesosphere.cosmos.service.ServiceUninstaller
import com.mesosphere.universe
import com.mesosphere.util.UrlSchemeHeader
import com.netaporter.uri.Uri
import com.twitter.app.App
import com.twitter.finagle.Http
import com.twitter.finagle.ListeningServer
import com.twitter.finagle.Service
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.finagle.http.filter.CommonLogFormatter
import com.twitter.finagle.http.filter.LoggingFilter
import com.twitter.finagle.param.Label
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.server.Admin
import com.twitter.server.AdminHttpServer
import com.twitter.server.Lifecycle
import com.twitter.server.Stats
import com.twitter.util.Await
import com.twitter.util.Duration
import com.twitter.util.Try
import org.apache.curator.framework.CuratorFramework
import org.slf4j.Logger
import shapeless.:+:
import shapeless.CNil
import shapeless.HNil

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

    val zkUri = zookeeperUri()
    logger.info("Using {} for the ZooKeeper connection", zkUri)

    val zkClient = zookeeper.Clients.createAndInitialize(zkUri)
    onExit(zkClient.close())

    val sourcesStorage = ZkRepositoryList(zkClient, zkUri)
    onExit(sourcesStorage.close())

    val universeClient = UniverseClient(adminRouter)

    new Components(
      adminRouter,
      zkClient,
      sourcesStorage,
      universeClient,
      new PackageCollection(
        new RepositoryCache(
          sourcesStorage,
          universeClient
        )
      ),
      new MarathonPackageRunner(adminRouter),
      ServiceUninstaller(adminRouter),
      ServiceUpdater(adminRouter)
    )
  }

  final def buildHandlers(components: Components): Handlers = {
    import components._

    implicit val sr = statsReceiver

    new Handlers(
      // Keep alphabetized
      capabilities = new CapabilitiesHandler,
      packageDescribe = new PackageDescribeHandler(repositories),
      packageInstall = new PackageInstallHandler(repositories, packageRunner),
      packageList = new ListHandler(adminRouter),
      packageListVersions = new ListVersionsHandler(repositories),
      packageRender = new PackageRenderHandler(repositories),
      packageRepositoryAdd = new PackageRepositoryAddHandler(sourcesStorage, universeClient),
      packageRepositoryDelete = new PackageRepositoryDeleteHandler(sourcesStorage),
      packageRepositoryList = new PackageRepositoryListHandler(sourcesStorage),
      packageResource = ResourceProxyHandler(repositories, proxyContentLimit()),
      packageSearch = new PackageSearchHandler(repositories),
      packageUninstall = new UninstallHandler(adminRouter, repositories, marathonSdkJanitor),
      serviceDescribe = new ServiceDescribeHandler(adminRouter, repositories),
      serviceUpdate = new ServiceUpdateHandler(adminRouter, repositories, serviceUpdater)
    )
  }

  final def buildEndpoints(handlers: Handlers): Endpoints = {
    import handlers._

    val pkg = "package"
    val repo = "repository"

    Endpoints(
      // Keep alphabetized
      capabilities = route(get("capabilities"), capabilities)(RequestValidators.noBody),
      packageDescribe = standardEndpoint(pkg :: "describe", packageDescribe),
      packageInstall = standardEndpoint(pkg :: "install", packageInstall),
      packageList = standardEndpoint(pkg :: "list", packageList),
      packageListVersions = standardEndpoint(pkg :: "list-versions", packageListVersions),
      packageRender = standardEndpoint(pkg :: "render", packageRender),
      packageRepositoryAdd = standardEndpoint(pkg :: repo :: "add", packageRepositoryAdd),
      packageRepositoryDelete = standardEndpoint(pkg :: repo :: "delete", packageRepositoryDelete),
      packageRepositoryList = standardEndpoint(pkg :: repo :: "list", packageRepositoryList),
      packageResource = get(pkg :: "resource" :: RequestValidators.proxyValidator).mapOutputAsync(packageResource(_)),
      packageSearch = standardEndpoint(pkg :: "search", packageSearch),
      packageUninstall = standardEndpoint(pkg :: "uninstall", packageUninstall),
      serviceDescribe = standardEndpoint("service" :: "describe", serviceDescribe),
      serviceUpdate = standardEndpoint("service" :: "update", serviceUpdate)
    )
  }

  protected final def start[A](allEndpoints: Endpoint[A])(implicit
    tr: ToResponse.Aux[A, Application.Json]
  ): Unit = {
    HttpProxySupport.configureProxySupport()
    implicit val sr = statsReceiver

    val service = logWrapper(buildService(allEndpoints))
    val maybeHttpServer = startServer(service.map { request: Request =>
      request.headerMap.add(UrlSchemeHeader, "http")
      request
    })
    val maybeHttpsServer = startTlsServer(service.map { request: Request =>
      request.headerMap.add(UrlSchemeHeader, "https")
      request
    })

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

  private[this] def logWrapper(service: Service[Request, Response]): Service[Request, Response] = {
    object CustomLoggingFilter extends LoggingFilter[Request](
      log = com.twitter.logging.Logger("access"),
      formatter = new CommonLogFormatter {
        override def format(request: Request, response: Response, responseTime: Duration): String = {
          val remoteAddr = request.remoteAddress.getHostAddress

          val contentLength = response.length
          val contentLengthStr = if (contentLength > 0) contentLength.toString else "-"

          val builder = new StringBuilder
          builder.append(remoteAddr)
          builder.append(" - \"")
          builder.append(escape(request.method.toString))
          builder.append(' ')
          builder.append(escape(request.uri))
          builder.append(' ')
          builder.append(escape(request.version.toString))
          builder.append("\" ")
          builder.append(response.statusCode.toString)
          builder.append(' ')
          builder.append(contentLengthStr)
          builder.append("B ")
          builder.append(responseTime.inMillis)
          builder.append("ms \"")
          builder.append(escape(request.userAgent.getOrElse("-")))
          builder.append('"')

          if (response.statusCode / 100 != 2) {
            val headersMap = request.headerMap
            headersMap.get(Fields.Authorization) match {
              case Some(_) => headersMap.put(Fields.Authorization, "********")
              case None => ()
            }
            builder.append(s" Headers : (${headersMap.map(_.productIterator.mkString(":")).mkString(", ")})")
          }

          builder.toString
        }
      }
    )
    CustomLoggingFilter andThen service
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

  private[this] def buildService[A](endpoints: Endpoint[A])(implicit
    statsReceiver: StatsReceiver,
    tr: ToResponse.Aux[A, Application.Json]
  ): Service[Request, Response] = {
    val stats = statsReceiver.scope("errorFilter")

    val api = endpoints.handle {
      case ce: CosmosException =>
        logger.info(s"Cosmos Exception : ${ce.getMessage}", ce)
        stats.counter(s"definedError/${sanitizeClassName(ce.error.getClass)}").incr()
        val output = Output.failure(
          ce,
          Status.fromCode(ce.error.status.code)
        ).withHeader(
          Fields.ContentType -> MediaTypes.ErrorResponse.show
        )
        ce.headers.foldLeft(output) { case (out, kv) => out.withHeader(kv) }
      case fe @ (_: _root_.io.finch.Error | _: _root_.io.finch.Errors) =>
        logger.warn(s"Finch Exception : ${fe.getMessage}", fe)
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
    val zkClient: CuratorFramework,
    val sourcesStorage: PackageSourcesStorage,
    val universeClient: UniverseClient,
    val repositories: PackageCollection,
    val packageRunner: MarathonPackageRunner,
    val marathonSdkJanitor: ServiceUninstaller,
    val serviceUpdater: ServiceUpdater
  )

  final class Handlers(
    // Keep alphabetized
    val capabilities: EndpointHandler[Unit, rpc.v1.model.CapabilitiesResponse],
    val packageDescribe: EndpointHandler[rpc.v1.model.DescribeRequest, universe.v4.model.PackageDefinition],
    val packageInstall: EndpointHandler[rpc.v1.model.InstallRequest, rpc.v2.model.InstallResponse],
    val packageList: EndpointHandler[rpc.v1.model.ListRequest, rpc.v1.model.ListResponse],
    val packageListVersions: EndpointHandler[rpc.v1.model.ListVersionsRequest, rpc.v1.model.ListVersionsResponse],
    val packageRender: EndpointHandler[rpc.v1.model.RenderRequest, rpc.v1.model.RenderResponse],
    val packageRepositoryAdd: EndpointHandler[rpc.v1.model.PackageRepositoryAddRequest, rpc.v1.model.PackageRepositoryAddResponse],
    val packageRepositoryDelete: EndpointHandler[rpc.v1.model.PackageRepositoryDeleteRequest, rpc.v1.model.PackageRepositoryDeleteResponse],
    val packageRepositoryList: EndpointHandler[rpc.v1.model.PackageRepositoryListRequest, rpc.v1.model.PackageRepositoryListResponse],
    val packageResource: ResourceProxyHandler,
    val packageSearch: EndpointHandler[rpc.v1.model.SearchRequest, rpc.v1.model.SearchResponse],
    val packageUninstall: EndpointHandler[rpc.v1.model.UninstallRequest, rpc.v1.model.UninstallResponse],
    val serviceDescribe: EndpointHandler[rpc.v1.model.ServiceDescribeRequest, rpc.v1.model.ServiceDescribeResponse],
    val serviceUpdate: EndpointHandler[rpc.v1.model.ServiceUpdateRequest, rpc.v1.model.ServiceUpdateResponse]
  )

  final case class Endpoints(
    // Keep alphabetized
    capabilities: Endpoint[Json],
    packageDescribe: Endpoint[Json],
    packageInstall: Endpoint[Json],
    packageList: Endpoint[Json],
    packageListVersions: Endpoint[Json],
    packageRender: Endpoint[Json],
    packageRepositoryAdd: Endpoint[Json],
    packageRepositoryDelete: Endpoint[Json],
    packageRepositoryList: Endpoint[Json],
    packageResource: Endpoint[Response],
    packageSearch: Endpoint[Json],
    packageUninstall: Endpoint[Json],
    serviceDescribe: Endpoint[Json],
    serviceUpdate: Endpoint[Json]
  ) {

    type Outputs =
      Json :+: Json :+: Json :+: Json :+: Json :+: Json :+: Json :+: Json :+: Json :+: Response :+:
        Json :+: Json :+: Json :+: Json :+: CNil

    def combine: Endpoint[Outputs] = {
      // Keep alphabetized
      (capabilities
        :+: packageDescribe
        :+: packageInstall
        :+: packageList
        :+: packageListVersions
        :+: packageRender
        :+: packageRepositoryAdd
        :+: packageRepositoryDelete
        :+: packageRepositoryList
        :+: packageResource
        :+: packageSearch
        :+: packageUninstall
        :+: serviceDescribe
        :+: serviceUpdate)
    }

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
    httpInterface().map { interface =>
      Http
        .server
        .configured(Label("http"))
        .serve(interface, service)
    }
  }

  private def startTlsServer(
    service: Service[Request, Response]
  ): Option[ListeningServer] = {
    httpsInterface.getWithDefault.map { interface =>
      Http
        .server
        .configured(Label("https"))
        .withTransport.tls(
          certificatePath().toString,
          keyPath().toString,
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

}

object Cosmos extends CosmosApp {

  override def main(): Unit = {
    val components = buildComponents()
    val handlers = buildHandlers(components)
    val endpoints = buildEndpoints(handlers)
    start(endpoints.combine)
  }

}
