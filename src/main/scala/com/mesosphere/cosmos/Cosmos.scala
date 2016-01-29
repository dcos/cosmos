package com.mesosphere.cosmos

import java.io.{BufferedInputStream, ByteArrayInputStream, FileInputStream, InputStream}
import java.util.zip.ZipInputStream

import com.mesosphere.cosmos.http.{MediaTypes, EndpointHandler}
import com.mesosphere.cosmos.model.{
  DescribeRequest,
  InstallRequest,
  ListRequest,
  ListResponse,
  SearchRequest,
  UninstallRequest,
  UninstallResponse
}
import com.mesosphere.cosmos.endpoint.ListHandler
import com.twitter.finagle.Service
import com.twitter.finagle.http.exp.Multipart.{FileUpload, InMemoryFileUpload, OnDiskFileUpload}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.stats.{NullStatsReceiver, StatsReceiver}
import io.circe.syntax.EncoderOps
import io.github.benwhitehead.finch.FinchServer

import com.twitter.util.{Await, Future}
import io.circe.{Encoder, Json}
import io.circe.generic.auto._    // Required for auto-parsing case classes from JSON

import io.finch._
import io.finch.circe._

private[cosmos] final class Cosmos(
  packageCache: PackageCache,
  packageRunner: PackageRunner,
  uninstallHandler: EndpointHandler[UninstallRequest, UninstallResponse],
  listHandler: EndpointHandler[ListRequest, ListResponse]
  )(implicit
  statsReceiver: StatsReceiver = NullStatsReceiver
  ) {

  lazy val logger = org.slf4j.LoggerFactory.getLogger(classOf[Cosmos])

  implicit val baseScope = BaseScope(Some("app"))

  val FilenameRegex = """/?([^/]+/)*[^-./]+-[^/]*-[^-./]+\.zip""".r

  val validFileUpload = fileUpload("file")
    .should("have application/zip Content-type")(_.contentType == "application/zip")
    .should("have a filename matching <package>-<version>-<digest>.zip") { request =>
      FilenameRegex.unapplySeq(request.fileName).nonEmpty
    }

  val packageImport: Endpoint[Json] = {
    def respond(file: FileUpload): Output[Json] = {
      fileUploadBytes(file).flatMap { fileBytes =>
        if (nonEmptyArchive(fileBytes)) {
          Ok(Map("message" -> "Import successful!").asJson)
        } else {
          throw EmptyPackageImport()
        }
      }
    }

    post("v1" / "package" / "import" ? validFileUpload)(respond _)
  }

  val packageInstall: Endpoint[Json] = {

    def respond(reqBody: InstallRequest): Future[Output[Json]] = {
      PackageInstall.install(packageCache, packageRunner)(reqBody)
        .map(res => Ok(res.asJson))
    }

    post("v1" / "package" / "install" ? body.as[InstallRequest])(respond _)
  }

  val packageUninstall: Endpoint[Json] = {
    def respond(req: UninstallRequest): Future[Output[Json]] = {
      uninstallHandler(req).map {
        case resp => Ok(resp.asJson).withContentType(Some(uninstallHandler.produces.show))
      }
    }

    post("v1" / "package" / "uninstall" ? uninstallHandler.reader)(respond _)
  }

  val packageDescribe: Endpoint[Json] = {

    def respond(describe: DescribeRequest): Future[Output[Json]] = {
      packageCache.getPackageDescribe(describe)
    }

    post("v1" / "package" / "describe" ? body.as[DescribeRequest]) (respond _)
  }

  val packageSearch: Endpoint[Json] = {

    def respond(reqBody: SearchRequest): Future[Output[Json]] = {
      packageCache
        .getRepoIndex
        .map(PackageSearch.getSearchResults(reqBody.query, _))
        .map { searchResults =>
          Ok(searchResults.asJson)
        }
    }

    post("v1" / "package" / "search" ? body.as[SearchRequest]) (respond _)
  }

  val packageList: Endpoint[Json] = {
    def respond(request: ListRequest): Future[Output[Json]] = {
      listHandler(request).map { resp =>
        Ok(resp.asJson)
      }
    }

    post("package" / "list" ? body.as[ListRequest])(respond _)
  }

  def exceptionErrorResponse(t: Throwable): List[ErrorResponseEntry] = t match {
    case Error.NotPresent(item) =>
      List(ErrorResponseEntry("not_present", s"Item '${item.kind}' not present but required"))
    case Error.NotParsed(item, typ, cause) =>
      List(ErrorResponseEntry("not_parsed", s"Item '${item.kind}' unable to be parsed : '${cause.getMessage}'"))
    case Error.NotValid(item, rule) =>
      List(ErrorResponseEntry("not_valid", s"Item '${item.kind}' deemed invalid by rule: '$rule'"))
    case Error.RequestErrors(ts) =>
      ts.flatMap(exceptionErrorResponse).toList
    case ce: CosmosError =>
      List(ErrorResponseEntry(ce.getClass.getSimpleName, ce.getMessage))
    case t: Throwable =>
      List(ErrorResponseEntry("unhandled_exception", t.getMessage))
  }

  implicit val exceptionEncoder: Encoder[Exception] =
    Encoder.instance { e => ErrorResponse(exceptionErrorResponse(e)).asJson }

  val service: Service[Request, Response] = {
    val stats = {
      baseScope.name match {
        case Some(bs) if bs.nonEmpty => statsReceiver.scope(s"$bs/errorFilter")
        case _ => statsReceiver.scope("errorFilter")
      }
    }

    (packageImport
      :+: packageInstall
      :+: packageDescribe
      :+: packageSearch
      :+: packageUninstall
      :+: packageList
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
    * @param clazz the class whose name is to be santised
    * @return The name of the specified class with all "illegal characters" replaced with '.'
    */
  private[this] def sanitiseClassName(clazz: Class[_]): String = {
    clazz.getName.replaceAllLiterally("$", ".")
  }

  /** Attempts to provide access to the content of the given file upload as a byte stream.
    *
    * @param file the file upload to get the content for
    * @return the file's content as a byte stream, if it was available; an error message otherwise.
    */
  private[this] def fileUploadBytes(file: FileUpload): Output[InputStream] = {
    file match {
      case InMemoryFileUpload(content, _, _, _) =>
        val bytes = Array.ofDim[Byte](content.length)
        content.write(bytes, 0)
        Output.payload(new ByteArrayInputStream(bytes))
      case OnDiskFileUpload(content, _, _, _) =>
        Output.payload(new BufferedInputStream(new FileInputStream(content)))
      case _ => Output.failure(new Exception("Unknown file upload type"), Status.NotImplemented)
    }
  }

  /** Determines whether the given byte stream encodes a Zip archive containing at least one file.
    *
    * @param packageBytes the byte stream to test
    * @return `true` if the byte stream is a non-empty Zip archive; `false` otherwise.
    */
  private[this] def nonEmptyArchive(packageBytes: InputStream): Boolean = {
    val zis = new ZipInputStream(packageBytes)
    try {
      val entryOpt = Option(zis.getNextEntry)
      entryOpt.foreach(_ => zis.closeEntry())
      entryOpt.nonEmpty
    } finally {
      zis.close()
    }
  }
}

object Cosmos extends FinchServer {
  def service = {
    val host = dcosHost()
    logger.info("Connecting to DCOS Cluster at: {}", host.toStringRaw)

    val boot = Services.adminRouterClient(host) map { dcosClient =>
      val adminRouter = new AdminRouter(host, dcosClient)

      val universeBundle = universeBundleUri()
      val universeDir = universeCacheDir()
      val packageCache = Await.result(UniversePackageCache(universeBundle, universeDir))

      val cosmos = new Cosmos(
        packageCache,
        new MarathonPackageRunner(adminRouter),
        new UninstallHandler(adminRouter),
        new ListHandler(adminRouter, packageCache)
      )
      cosmos.service
    }
    boot.get
  }
}
