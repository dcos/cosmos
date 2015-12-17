package com.mesosphere.cosmos

import java.io.{BufferedInputStream, ByteArrayInputStream, FileInputStream, InputStream}
import java.util.zip.ZipInputStream

import com.twitter.finagle.http.exp.Multipart.{FileUpload, InMemoryFileUpload, OnDiskFileUpload}
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.{Http, Service}
import com.twitter.util.{Await, Future, Try}
import io.circe.generic.auto._    // Required for auto-parsing case classes from JSON
import io.finch._

private final class Cosmos(packageCache: PackageCache, packageRunner: PackageRunner) {

  val FilenameRegex = """/?([^/]+/)*[^-./]+-[^/]*-[^-./]+\.zip""".r

  val validFileUpload = safeFileUpload("file")
    .should("have application/zip Content-type")(_.contentType == "application/zip")
    .should("have a filename matching <package>-<version>-<digest>.zip") { request =>
      FilenameRegex.unapplySeq(request.fileName).nonEmpty
    }

  val ping: Endpoint[String] = get("ping") { Ok("pong") }

  val packageImport: Endpoint[String] =
    post("v1" / "package" / "import" ? validFileUpload) { file: FileUpload =>
      fileUploadBytes(file).flatMap { fileBytes =>
        if (nonEmptyArchive(fileBytes)) {
          Ok("Import successful!\n")
        } else {
          BadRequest(new Exception("Package is empty"))
        }
      }
    }

  val packageInstall: Endpoint[String] = {
    // Required for parsing case classes from JSON; interferes with the other endpoints
    import io.finch.circe._

    post("v1" / "package" / "install" ? body.as[InstallRequest]) { (reqBody: InstallRequest) =>
      packageCache.get(reqBody.name) match {
        case Some(marathonJson) => packageRunner.launch(marathonJson)
        case _ => Future.value(NotFound(new Exception(s"Package [${reqBody.name}] not found")))
      }
    }
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

object Cosmos {

  def main(args: Array[String]): Unit = {
    val adminRouter = Http.newService(s"${Config.DcosHost}:80")
    val cosmos = new Cosmos(PackageCache.empty, new MarathonPackageRunner(adminRouter))
    val _ = Await.ready(Http.serve(":8080", cosmos.service))
  }

}
