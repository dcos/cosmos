package com.mesosphere.cosmos

import java.nio.file.Path

import com.netaporter.uri.Uri
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.stats.{NullStatsReceiver, StatsReceiver}
import com.twitter.util.Future
import com.twitter.util.Try
import io.circe.Json
import io.circe.syntax.EncoderOps
import io.finch._
import io.finch.circe._
import io.github.benwhitehead.finch.FinchServer
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry

import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.handler._
import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.repository.UniverseClient
import com.mesosphere.cosmos.repository.ZooKeeperStorage

private[cosmos] final class Cosmos(
  uninstallHandler: EndpointHandler[UninstallRequest, UninstallResponse],
  packageInstallHandler: EndpointHandler[InstallRequest, InstallResponse],
  packageRenderHandler: EndpointHandler[RenderRequest, RenderResponse],
  packageSearchHandler: EndpointHandler[SearchRequest, SearchResponse],
  packageDescribeHandler: EndpointHandler[DescribeRequest, DescribeResponse],
  packageListVersionsHandler: EndpointHandler[ListVersionsRequest, ListVersionsResponse],
  listHandler: EndpointHandler[ListRequest, ListResponse],
  listRepositoryHandler: EndpointHandler[PackageRepositoryListRequest, PackageRepositoryListResponse],
  addRepositoryHandler: EndpointHandler[PackageRepositoryAddRequest, PackageRepositoryAddResponse],
  deleteRepositoryHandler: EndpointHandler[PackageRepositoryDeleteRequest, PackageRepositoryDeleteResponse],
  capabilitiesHandler: CapabilitiesHandler
)(implicit statsReceiver: StatsReceiver = NullStatsReceiver) {
  lazy val logger = org.slf4j.LoggerFactory.getLogger(classOf[Cosmos])

  val packageInstall: Endpoint[Json] = {

    def respond(t: (RequestSession, InstallRequest)): Future[Output[Json]] = {
      implicit val (session, request) = t
      packageInstallHandler(request)
        .map(res => Ok(res.asJson).withContentType(Some(packageInstallHandler.produces.show)))
    }

    post("package" / "install" ? packageInstallHandler.reader)(respond _)
  }

  val packageUninstall: Endpoint[Json] = {
    def respond(t: (RequestSession, UninstallRequest)): Future[Output[Json]] = {
      implicit val (session, request) = t
      uninstallHandler(request).map {
        case resp => Ok(resp.asJson).withContentType(Some(uninstallHandler.produces.show))
      }
    }

    post("package" / "uninstall" ? uninstallHandler.reader)(respond _)
  }

  val packageDescribe: Endpoint[Json] = {

    def respond(t: (RequestSession, DescribeRequest)): Future[Output[Json]] = {
      implicit val (session, request) = t
      packageDescribeHandler(request) map { resp =>
        Ok(resp.asJson).withContentType(Some(packageDescribeHandler.produces.show))
      }
    }

    post("package" / "describe" ? packageDescribeHandler.reader) (respond _)
  }

  val packageRender: Endpoint[Json] = {

    def respond(t: (RequestSession, RenderRequest)): Future[Output[Json]] = {
      implicit val (session, request) = t
      packageRenderHandler(request)
        .map(res => Ok(res.asJson).withContentType(Some(packageRenderHandler.produces.show)))
    }

    post("package" / "render" ? packageRenderHandler.reader)(respond _)
  }

  val packageListVersions: Endpoint[Json] = {
    def respond(t: (RequestSession, ListVersionsRequest)): Future[Output[Json]] = {
      implicit val (session, request) = t
      packageListVersionsHandler(request) map { resp =>
        Ok(resp.asJson).withContentType(Some(packageListVersionsHandler.produces.show))
      }
    }

    post("package" / "list-versions" ? packageListVersionsHandler.reader) (respond _)
  }

  val packageSearch: Endpoint[Json] = {

    def respond(t: (RequestSession, SearchRequest)): Future[Output[Json]] = {
      implicit val (session, request) = t
      packageSearchHandler(request)
        .map { searchResults =>
          Ok(searchResults.asJson).withContentType(Some(packageSearchHandler.produces.show))
        }
    }

    post("package" / "search" ? packageSearchHandler.reader) (respond _)
  }

  val packageList: Endpoint[Json] = {
    def respond(t: (RequestSession, ListRequest)): Future[Output[Json]] = {
      implicit val (session, request) = t
      listHandler(request).map { resp =>
        Ok(resp.asJson).withContentType(Some(listHandler.produces.show))
      }
    }

    post("package" / "list" ? listHandler.reader)(respond _)
  }

  val capabilities: Endpoint[Json] = {
    def respond(t: (RequestSession, Any)): Future[Output[Json]] = {
      implicit val (session, _) = t
      capabilitiesHandler(None).map { resp =>
        Ok(resp.asJson).withContentType(Some(capabilitiesHandler.produces.show))
      }
    }

    get("capabilities" ? capabilitiesHandler.reader)(respond _)
  }

  val packageListSources: Endpoint[Json] = {
    def respond(t: (RequestSession, PackageRepositoryListRequest)): Future[Output[Json]] = {
      implicit val (session, request) = t
      listRepositoryHandler(request).map { response =>
        Ok(response.asJson).withContentType(Some(listRepositoryHandler.produces.show))
      }
    }

    post("package" / "repository"/ "list" ? listRepositoryHandler.reader)(respond _)
  }

  val packageAddSource: Endpoint[Json] = {
    def respond(t: (RequestSession, PackageRepositoryAddRequest)): Future[Output[Json]] = {
      implicit val (session, request) = t
      addRepositoryHandler(request).map { response =>
        Ok(response.asJson).withContentType(Some(addRepositoryHandler.produces.show))
      }
    }

    post("package" / "repository" / "add" ? addRepositoryHandler.reader)(respond _)
  }

  val packageDeleteSource: Endpoint[Json] = {
    def respond(t: (RequestSession, PackageRepositoryDeleteRequest)): Future[Output[Json]] = {
      implicit val (session, request) = t
      deleteRepositoryHandler(request).map { response =>
        Ok(response.asJson).withContentType(Some(deleteRepositoryHandler.produces.show))
      }
    }

    post("package" / "repository" / "delete" ? deleteRepositoryHandler.reader)(respond _)
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
        val mar: Uri = dcosHost / "marathon"
        val mesos: Uri = dcosHost / "mesos"
        mar -> mesos
      }
      .handle {
        case _: IllegalArgumentException =>
          val mar: Uri = marathonUri().toStringRaw
          val master: Uri = mesosMasterUri().toStringRaw
          logger.info("Connecting to Marathon at: {}", mar)
          logger.info("Connecting to Mesos master at: {}", master)
          mar -> master
      }
      .flatMap { case (marathon, mesosMaster) =>
        Trys.join(
          Services.marathonClient(marathon).map { marathon -> _ },
          Services.mesosClient(mesosMaster).map { mesosMaster -> _ }
        )
      }
      .map { case (marathon, mesosMaster) =>
        new AdminRouter(
          new MarathonClient(marathon._1, marathon._2),
          new MesosMasterClient(mesosMaster._1, mesosMaster._2)
        )
      }

    val boot = ar map { adminRouter =>
      val dd = dataDir()
      logger.info("Using {} for data directory", dd)

      val zkUri = zookeeperUri()
      logger.info("Using {} for the zookeeper connection", zkUri)

      val marathonPackageRunner = new MarathonPackageRunner(adminRouter)

      val universeClient = UniverseClient()

      val zkRetryPolicy = new ExponentialBackoffRetry(1000, 3)
      val zkClient = CuratorFrameworkFactory.builder()
        .namespace(zkUri.path.stripPrefix("/"))
        .connectString(zkUri.connectString)
        .retryPolicy(zkRetryPolicy)
        .build

      // Start the client and close it on exit
      zkClient.start()
      onExit {
        zkClient.close()
      }

      val sourcesStorage = new ZooKeeperStorage(zkClient)()

      val cosmos = Cosmos(adminRouter, marathonPackageRunner, sourcesStorage, universeClient, dd)
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
      CapabilitiesHandler()
    )(statsReceiver)
  }

}
