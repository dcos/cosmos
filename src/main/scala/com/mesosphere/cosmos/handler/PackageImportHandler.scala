package com.mesosphere.cosmos.handler

import java.io.{BufferedInputStream, ByteArrayInputStream, FileInputStream, InputStream}
import java.util.zip.ZipInputStream

import com.mesosphere.cosmos.{EmptyPackageImport, FileUploadError}
import com.twitter.finagle.http.exp.Multipart.{FileUpload, InMemoryFileUpload, OnDiskFileUpload}
import com.twitter.util.Future
import io.circe.Json
import io.circe.syntax._
import io.finch._

private[cosmos] class PackageImportHandler extends Function1[FileUpload, Future[Json]] {

//  override def accepts: MediaType = MediaType("application", "zip")
//  override def produces: MediaType = MediaType("application", "json")  //TODO: Real type

  val FilenameRegex = """/?([^/]+/)*[^-./]+-[^/]*-[^-./]+\.zip""".r

  val reader = fileUpload("file")
    .should("have application/zip Content-type")(_.contentType == "application/zip")
    .should("have a filename matching <package>-<version>-<digest>.zip") { request =>
      FilenameRegex.unapplySeq(request.fileName).nonEmpty
    }

  override def apply(file: FileUpload): Future[Json] = {
    val fileBytes = fileUploadBytes(file)
    if (nonEmptyArchive(fileBytes)) {
      Future.value(Map("message" -> "Import successful!").asJson)
    } else {
      throw EmptyPackageImport()
    }
  }

  /** Attempts to provide access to the content of the given file upload as a byte stream.
    *
    * @param file the file upload to get the content for
    * @return the file's content as a byte stream, if it was available; an error message otherwise.
    */
  private[this] def fileUploadBytes(file: FileUpload): InputStream = {
    file match {
      case InMemoryFileUpload(content, _, _, _) =>
        val bytes = Array.ofDim[Byte](content.length)
        content.write(bytes, 0)
        new ByteArrayInputStream(bytes)
      case OnDiskFileUpload(content, _, _, _) =>
        new BufferedInputStream(new FileInputStream(content))
      case _ => throw new FileUploadError("Unknown file upload type")
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
