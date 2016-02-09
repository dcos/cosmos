package com.mesosphere.cosmos

import com.mesosphere.cosmos.handler._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http.exp.Multipart.FileUpload
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.stats.{NullStatsReceiver, StatsReceiver}
import io.circe.syntax.EncoderOps
import io.github.benwhitehead.finch.FinchServer

import com.twitter.util.Future
import com.twitter.util.Try
import io.circe.Json
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._

import io.finch._
import io.finch.circe._

private[cosmos] final class Cosmos(
  packageCache: PackageCache,
  packageRunner: PackageRunner,
  uninstallHandler: EndpointHandler[UninstallRequest, UninstallResponse],
  packageInstallHandler: EndpointHandler[InstallRequest, InstallResponse],
  packageRenderHandler: EndpointHandler[RenderRequest, RenderResponse],
  packageSearchHandler: EndpointHandler[SearchRequest, SearchResponse],
  packageImportHandler: PackageImportHandler, // TODO: Real response Type
  packageDescribeHandler: EndpointHandler[DescribeRequest, DescribeResponse],
  packageListVersionsHandler: EndpointHandler[ListVersionsRequest, ListVersionsResponse],
  listHandler: EndpointHandler[ListRequest, ListResponse],
  capabilitiesHandler: CapabilitiesHandler
)(implicit statsReceiver: StatsReceiver = NullStatsReceiver) {
  lazy val logger = org.slf4j.LoggerFactory.getLogger(classOf[Cosmos])

  implicit val baseScope = BaseScope(Some("app"))


  val packageImport: Endpoint[Json] = {
    def respond(file: FileUpload): Future[Output[Json]] = {
      packageImportHandler(file).map { output =>
        Ok(output)
      }
    }

    post("package" / "import" ? packageImportHandler.reader)(respond _)
  }

  val packageInstall: Endpoint[Json] = {

    def respond(reqBody: InstallRequest): Future[Output[Json]] = {
      packageInstallHandler(reqBody)
        .map(res => Ok(res.asJson).withContentType(Some(packageInstallHandler.produces.show)))
    }

    post("package" / "install" ? packageInstallHandler.reader)(respond _)
  }

  val packageUninstall: Endpoint[Json] = {
    def respond(req: UninstallRequest): Future[Output[Json]] = {
      uninstallHandler(req).map {
        case resp => Ok(resp.asJson).withContentType(Some(uninstallHandler.produces.show))
      }
    }

    post("package" / "uninstall" ? uninstallHandler.reader)(respond _)
  }

  val packageDescribe: Endpoint[Json] = {

    def respond(describe: DescribeRequest): Future[Output[Json]] = {
      packageDescribeHandler(describe) map { resp =>
        Ok(resp.asJson).withContentType(Some(packageDescribeHandler.produces.show))
      }
    }

    post("package" / "describe" ? packageDescribeHandler.reader) (respond _)
  }

  val packageRender: Endpoint[Json] = {

    def respond(reqBody: RenderRequest): Future[Output[Json]] = {
      packageRenderHandler(reqBody)
        .map(res => Ok(res.asJson).withContentType(Some(packageRenderHandler.produces.show)))
    }

    post("package" / "render" ? packageRenderHandler.reader)(respond _)
  }

  val packageListVersions: Endpoint[Json] = {
    def respond(listVersions: ListVersionsRequest): Future[Output[Json]] = {
      packageListVersionsHandler(listVersions) map { resp =>
        Ok(resp.asJson).withContentType(Some(packageListVersionsHandler.produces.show))
      }
    }

    post("package" / "list-versions" ? packageListVersionsHandler.reader) (respond _)
  }

  val packageSearch: Endpoint[Json] = {

    def respond(reqBody: SearchRequest): Future[Output[Json]] = {
      packageSearchHandler(reqBody)
        .map { searchResults =>
          Ok(searchResults.asJson).withContentType(Some(packageSearchHandler.produces.show))
        }
    }

    post("package" / "search" ? packageSearchHandler.reader) (respond _)
  }

  val packageList: Endpoint[Json] = {
    def respond(request: ListRequest): Future[Output[Json]] = {
      listHandler(request).map { resp =>
        Ok(resp.asJson).withContentType(Some(listHandler.produces.show))
      }
    }

    post("package" / "list" ? listHandler.reader)(respond _)
  }

  val capabilities: Endpoint[Json] = {
    def respond(any: Any): Future[Output[Json]] = {
      capabilitiesHandler(None).map { resp =>
        Ok(resp.asJson).withContentType(Some(capabilitiesHandler.produces.show))
      }
    }

    get("capabilities" ? capabilitiesHandler.reader)(respond _)
  }

  val service: Service[Request, Response] = {
    val stats = {
      baseScope.name match {
        case Some(bs) if bs.nonEmpty => statsReceiver.scope(s"$bs/errorFilter")
        case _ => statsReceiver.scope("errorFilter")
      }
    }

    (packageImport
      :+: packageInstall
      :+: packageRender
      :+: packageDescribe
      :+: packageSearch
      :+: packageUninstall
      :+: packageListVersions
      :+: packageList
      :+: capabilities
    )
      .handle {
        case ce: CosmosError =>
          stats.counter(s"definedError/${sanitiseClassName(ce.getClass)}").incr()
          Output.failure(ce, ce.status).withContentType(Some(MediaTypes.ErrorResponse.show))
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
      val universeBundle = universeBundleUri()
      val dd = dataDir()
      logger.info("Using {} for data directory", dd)
      val packageCache = UniversePackageCache(universeBundle, dd)
      val marathonPackageRunner = new MarathonPackageRunner(adminRouter)

      val cosmos = new Cosmos(
        packageCache,
        marathonPackageRunner,
        new UninstallHandler(adminRouter, packageCache),
        new PackageInstallHandler(packageCache, marathonPackageRunner),
        new PackageRenderHandler(packageCache),
        new PackageSearchHandler(packageCache),
        new PackageImportHandler,
        new PackageDescribeHandler(packageCache),
        new ListVersionsHandler(packageCache),
        new ListHandler(adminRouter, packageCache),
        CapabilitiesHandler()
      )
      cosmos.service
    }
    boot.get
  }
}
