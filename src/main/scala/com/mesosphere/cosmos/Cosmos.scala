package com.mesosphere.cosmos

import java.io.{BufferedInputStream, ByteArrayInputStream, FileInputStream, InputStream}
import java.util.zip.ZipInputStream

import cats.data.Xor.{Left, Right}
import com.mesosphere.cosmos.model.{UninstallResponse, DescribeRequest, InstallRequest, UninstallRequest}
import com.twitter.finagle.Service
import com.twitter.finagle.http.exp.Multipart.{FileUpload, InMemoryFileUpload, OnDiskFileUpload}
import com.twitter.finagle.http.{Request, Response, Status}
import io.circe.syntax.EncoderOps
import io.github.benwhitehead.finch.FinchServer

import com.twitter.util.{Await, Future}
import io.circe.{Json, JsonObject}
import io.circe.generic.auto._    // Required for auto-parsing case classes from JSON

import io.finch._
import io.finch.circe._

private final class Cosmos(
  packageCache: PackageCache,
  packageRunner: PackageRunner,
  uninstallHandler: (UninstallRequest) => Future[CosmosResult[UninstallResponse]]
) {

  val FilenameRegex = """/?([^/]+/)*[^-./]+-[^/]*-[^-./]+\.zip""".r

  val validFileUpload = fileUpload("file")
    .should("have application/zip Content-type")(_.contentType == "application/zip")
    .should("have a filename matching <package>-<version>-<digest>.zip") { request =>
      FilenameRegex.unapplySeq(request.fileName).nonEmpty
    }

  val ping: Endpoint[Json] = get("ping") { successOutput("pong") }

  val packageImport: Endpoint[Json] = {
    def respond(file: FileUpload): Output[Json] = {
      fileUploadBytes(file).flatMap { fileBytes =>
        if (nonEmptyArchive(fileBytes)) {
          successOutput("Import successful!")
        } else {
          failureOutput(errorNel(EmptyPackageImport))
        }
      }
    }

    post("v1" / "package" / "import" ? validFileUpload)(respond _)
  }

  val packageInstall: Endpoint[Json] = {

    def respond(reqBody: InstallRequest): Future[Output[Json]] = {
      packageCache
        .getPackageFiles(reqBody.name, reqBody.version)
        .map(_.flatMap(PackageInstall.preparePackageConfig(reqBody, _)))
        .flatMap {
          case Right(marathonJson) => packageRunner.launch(marathonJson)
          case Left(errors) => Future.value(failureOutput(errors))
        }
    }

    post("v1" / "package" / "install" ? body.as[InstallRequest])(respond _)
  }

  val packageUninstall: Endpoint[Json] = {
    def respond(req: UninstallRequest): Future[Output[Json]] = {
      uninstallHandler(req).map {
        case Right(r) => Ok(r.asJson)
        case Left(err) => failureOutput(err)
      }
    }

    post("v1" / "package" / "uninstall" ? body.as[UninstallRequest])(respond _)
  }

  val packageDescribe: Endpoint[Json] = {

    def respond(describe: DescribeRequest): Future[Output[Json]] = {
      packageCache
        .getPackageFiles(describe.packageName, describe.packageVersion)
        .map { packageFilesXor =>
          val responseContentXor = packageFilesXor.map(_.describeAsJson)

          responseContentXor match {
            case Right(json) => Ok(json)
            case Left(errors) => failureOutput(errors)
          }
        }
    }

    val describe: RequestReader[DescribeRequest] = for {
        name <- param("packageName")
        version <- paramOption("packageVersion")
    } yield DescribeRequest(name, version)

    get("v1" / "package" / "describe" ? describe) (respond _)
  }

  val service: Service[Request, Response] =
    (ping
      :+: packageImport
      :+: packageInstall
      :+: packageDescribe
      :+: packageUninstall
    ).toService

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

    val dcosClient = Services.adminRouterClient(dcosHost())
    val adminRouter = new AdminRouter(dcosHost(), dcosClient)

    val universeBundle = universeBundleUri()
    val universeDir = universeCacheDir()
    val packageCache = Await.result(UniversePackageCache(universeBundle, universeDir))

    val cosmos = new Cosmos(
      packageCache,
      new MarathonPackageRunner(adminRouter),
      new UninstallHandler(adminRouter)
    )
    cosmos.service
  }

}
