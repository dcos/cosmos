package com.mesosphere.cosmos

import java.io.{BufferedInputStream, ByteArrayInputStream, FileInputStream, InputStream}
import java.util.zip.ZipInputStream

import cats.data.Xor.{Left, Right}
import com.mesosphere.cosmos.model.InstallRequest
import com.twitter.finagle.Service
import com.twitter.finagle.http.exp.Multipart.{FileUpload, InMemoryFileUpload, OnDiskFileUpload}
import com.twitter.finagle.http.{Request, Response, Status}
import io.github.benwhitehead.finch.FinchServer

// Required for auto-parsing case classes from JSON
import com.twitter.util.{Await, Future}
import io.circe.Json
import io.circe.generic.auto._    // Required for auto-parsing case classes from JSON
import io.finch._
import io.finch.circe._

private final class Cosmos(packageCache: PackageCache, packageRunner: PackageRunner) {

  val FilenameRegex = """/?([^/]+/)*[^-./]+-[^/]*-[^-./]+\.zip""".r

  val validFileUpload = safeFileUpload("file")
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
        .getMarathonJson(reqBody.name, reqBody.version)
        .flatMap {
          case Right(marathonJson) => packageRunner.launch(marathonJson)
          case Left(errors) => Future.value(failureOutput(errors))
        }
    }

    post("v1" / "package" / "install" ? body.as[InstallRequest])(respond _)
  }

  val service: Service[Request, Response] = (ping :+: packageImport :+: packageInstall).toService

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

    val cosmos = new Cosmos(packageCache, new MarathonPackageRunner(adminRouter))
    cosmos.service
  }

}
