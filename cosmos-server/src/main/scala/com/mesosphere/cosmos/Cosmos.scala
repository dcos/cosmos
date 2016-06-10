package com.mesosphere.cosmos

import java.nio.file.Path

import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.circe.{DispatchingMediaTypedEncoder, MediaTypedDecoder, MediaTypedEncoder}
import com.mesosphere.cosmos.handler._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.{PackageSourcesStorage, UniverseClient, ZooKeeperStorage}
import com.mesosphere.universe.v3.circe.{Encoders => V3Encoders}
import com.mesosphere.universe.v3.model.V3Package
import com.netaporter.uri.Uri
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.stats.{NullStatsReceiver, StatsReceiver}
import com.twitter.util.Try
import io.circe.Json
import io.finch._
import io.finch.circe._
import io.github.benwhitehead.finch.FinchServer
import shapeless.HNil

private[cosmos] final class Cosmos(
  uninstallHandler: EndpointHandler[UninstallRequest, UninstallResponse],
  packageInstallHandler: EndpointHandler[InstallRequest, InstallResponse],
  packageRenderHandler: EndpointHandler[RenderRequest, RenderResponse],
  packageSearchHandler: EndpointHandler[SearchRequest, SearchResponse],
  packageDescribeHandler: EndpointHandler[DescribeRequest, model.v1.DescribeResponse],
  packageListVersionsHandler: EndpointHandler[ListVersionsRequest, ListVersionsResponse],
  listHandler: EndpointHandler[ListRequest, ListResponse],
  listRepositoryHandler: EndpointHandler[PackageRepositoryListRequest, PackageRepositoryListResponse],
  addRepositoryHandler: EndpointHandler[PackageRepositoryAddRequest, PackageRepositoryAddResponse],
  deleteRepositoryHandler: EndpointHandler[PackageRepositoryDeleteRequest, PackageRepositoryDeleteResponse],
  capabilitiesHandler: CapabilitiesHandler
)(implicit statsReceiver: StatsReceiver = NullStatsReceiver) {

  import Cosmos._

  lazy val logger = org.slf4j.LoggerFactory.getLogger(classOf[Cosmos])

  val packageInstall: Endpoint[Json] = {
    route(post("package" / "install"), packageInstallHandler)(RequestReaders.standard)
  }

  val packageUninstall: Endpoint[Json] = {
    route(post("package" / "uninstall"), uninstallHandler)(RequestReaders.standard)
  }

  val packageDescribe: Endpoint[Json] = {
    route(post("package" / "describe"), packageDescribeHandler)(RequestReaders.standard)
  }

  val packageRender: Endpoint[Json] = {
    route(post("package" / "render"), packageRenderHandler)(RequestReaders.standard)
  }

  val packageListVersions: Endpoint[Json] = {
    route(post("package" / "list-versions"), packageListVersionsHandler)(RequestReaders.standard)
  }

  val packageSearch: Endpoint[Json] = {
    route(post("package" / "search"), packageSearchHandler)(RequestReaders.standard)
  }

  val packageList: Endpoint[Json] = {
    route(post("package" / "list"), listHandler)(RequestReaders.standard)
  }

  val capabilities: Endpoint[Json] = {
    route(get("capabilities"), capabilitiesHandler)(RequestReaders.noBody)
  }

  val packageListSources: Endpoint[Json] = {
    route(post("package" / "repository" / "list"), listRepositoryHandler)(RequestReaders.standard)
  }

  val packageAddSource: Endpoint[Json] = {
    route(post("package" / "repository" / "add"), addRepositoryHandler)(RequestReaders.standard)
  }

  val packageDeleteSource: Endpoint[Json] = {
    route(post("package" / "repository" / "delete"), deleteRepositoryHandler)(RequestReaders.standard)
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
    )
      .handle {
        case ce: CosmosError =>
          stats.counter(s"definedError/${sanitiseClassName(ce.getClass)}").incr()
          val output = Output.failure(ce, ce.status).withContentType(Some(MediaTypes.ErrorResponse.show))
          ce.getHeaders.foldLeft(output) { case (out, kv) => out.withHeader(kv) }
        case fe: io.finch.Error =>
          stats.counter(s"finchError/${sanitiseClassName(fe.getClass)}").incr()
          Output.failure(fe, Status.BadRequest).withContentType(Some(MediaTypes.ErrorResponse.show))
        case e: Exception if !e.isInstanceOf[io.finch.Error] =>
          stats.counter(s"unhandledException/${sanitiseClassName(e.getClass)}").incr()
          logger.warn("Unhandled exception: ", e)
          Output.failure(e, Status.InternalServerError).withContentType(Some(MediaTypes.ErrorResponse.show))
        case t: Throwable if !t.isInstanceOf[io.finch.Error] =>
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
  def service = {
    implicit val stats = statsReceiver.scope("cosmos")
    import com.netaporter.uri.dsl._

    HttpProxySupport.configureProxySupport()

    val ar = Try(dcosUri())
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

    val boot = ar map { adminRouter =>
      val dd = dataDir()
      logger.info("Using {} for data directory", dd)

      val zkUri = zookeeperUri()
      logger.info("Using {} for the ZooKeeper connection", zkUri)

      val marathonPackageRunner = new MarathonPackageRunner(adminRouter)

      val zkClient = zookeeper.Clients.createAndInitialize(
        zkUri,
        sys.env.get("ZOOKEEPER_USER").zip(sys.env.get("ZOOKEEPER_SECRET")).headOption
      )
      onExit {
        zkClient.close()
      }

      val sourcesStorage = new ZooKeeperStorage(zkClient)()

      val cosmos = Cosmos(adminRouter, marathonPackageRunner, sourcesStorage, UniverseClient(), dd)
      cosmos.service
    }
    boot.get
  }

  private[cosmos] def apply(
    adminRouter: AdminRouter,
    packageRunner: PackageRunner,
    sourcesStorage: PackageSourcesStorage,
    universeClient: UniverseClient,
    dataDir: Path
  )(implicit statsReceiver: StatsReceiver = NullStatsReceiver): Cosmos = {

    val repositories = new MultiRepository(sourcesStorage, dataDir, universeClient)

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
      new CapabilitiesHandler
    )(statsReceiver)
  }

  private[cosmos] def route[Req, Res](base: Endpoint[HNil], handler: EndpointHandler[Req, Res])(
    requestReader: RequestReader[EndpointContext[Req, Res]]
  ): Endpoint[Json] = {
    (base ? requestReader).apply((context: EndpointContext[Req, Res]) => handler(context))
  }

  implicit val packageListDecoder: MediaTypedDecoder[ListRequest] =
    MediaTypedDecoder(MediaTypes.ListRequest)

  implicit val packageListVersionsDecoder: MediaTypedDecoder[ListVersionsRequest] =
    MediaTypedDecoder(MediaTypes.ListVersionsRequest)

  implicit val packageDescribeDecoder: MediaTypedDecoder[DescribeRequest] =
    MediaTypedDecoder(MediaTypes.DescribeRequest)

  implicit val packageInstallDecoder: MediaTypedDecoder[InstallRequest] =
    MediaTypedDecoder(MediaTypes.InstallRequest)

  implicit val packageRenderDecoder: MediaTypedDecoder[RenderRequest] =
    MediaTypedDecoder(MediaTypes.RenderRequest)

  implicit val packageRepositoryAddDecoder: MediaTypedDecoder[PackageRepositoryAddRequest] =
    MediaTypedDecoder(MediaTypes.PackageRepositoryAddRequest)

  implicit val packageRepositoryDeleteDecoder: MediaTypedDecoder[PackageRepositoryDeleteRequest] =
    MediaTypedDecoder(MediaTypes.PackageRepositoryDeleteRequest)

  implicit val packageRepositoryListDecoder: MediaTypedDecoder[PackageRepositoryListRequest] =
    MediaTypedDecoder(MediaTypes.PackageRepositoryListRequest)

  implicit val packageSearchDecoder: MediaTypedDecoder[SearchRequest] =
    MediaTypedDecoder(MediaTypes.SearchRequest)

  implicit val packageUninstallDecoder: MediaTypedDecoder[UninstallRequest] =
    MediaTypedDecoder(MediaTypes.UninstallRequest)

  implicit val capabilitiesEncoder: DispatchingMediaTypedEncoder[CapabilitiesResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.CapabilitiesResponse)

  implicit val packageListEncoder: DispatchingMediaTypedEncoder[ListResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.ListResponse)

  implicit val packageListVersionsEncoder: DispatchingMediaTypedEncoder[ListVersionsResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.ListVersionsResponse)

  implicit val packageDescribeV1Encoder: DispatchingMediaTypedEncoder[model.v1.DescribeResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.V1DescribeResponse)

  implicit val packageDescribeEncoder: DispatchingMediaTypedEncoder[model.DescribeResponse] = {
    DispatchingMediaTypedEncoder(Seq(
      MediaTypedEncoder(
        encoder = V3Encoders.encodeV3Package,
        mediaType = MediaTypes.V2DescribeResponse
      ),
      MediaTypedEncoder(
        encoder = encodeDescribeResponse.contramap(converter.Response.v3PackageToDescribeResponse),
        mediaType = MediaTypes.V1DescribeResponse
      )
    ))
  }

  implicit val packageInstallEncoder: DispatchingMediaTypedEncoder[InstallResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.InstallResponse)

  implicit val packageRenderEncoder: DispatchingMediaTypedEncoder[RenderResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.RenderResponse)

  implicit val packageRepositoryAddEncoder: DispatchingMediaTypedEncoder[PackageRepositoryAddResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.PackageRepositoryAddResponse)

  implicit val packageRepositoryDeleteEncoder: DispatchingMediaTypedEncoder[PackageRepositoryDeleteResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.PackageRepositoryDeleteResponse)

  implicit val packageRepositoryListEncoder: DispatchingMediaTypedEncoder[PackageRepositoryListResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.PackageRepositoryListResponse)

  implicit val packageSearchEncoder: DispatchingMediaTypedEncoder[SearchResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.SearchResponse)

  implicit val packageUninstallEncoder: DispatchingMediaTypedEncoder[UninstallResponse] =
    DispatchingMediaTypedEncoder(MediaTypes.UninstallResponse)
}
