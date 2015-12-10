package com.mesosphere.cosmos

import com.twitter.finagle.http.exp.Multipart.FileUpload
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, Http}
import com.twitter.util.Await

import io.finch._

object Cosmos {

  val FilenameRegex = """/?([^/]+/)*[^-./]+-[^/]*-[^-./]+\.zip""".r

  val validFileUpload = safeFileUpload("file")
    .should("have application/zip Content-type")(_.contentType == "application/zip")
    .should("have a filename matching <package>-<version>-<digest>.zip") { request =>
      FilenameRegex.unapplySeq(request.fileName).nonEmpty
    }

  val ping: Endpoint[String] = get("ping") { Ok("pong") }

  val packageImport: Endpoint[String] =
    post("v1" / "package" / "import" ? validFileUpload) { _: FileUpload =>
      Ok("Import successful!\n")
    }

  val service: Service[Request, Response] = (ping :+: packageImport).toService

  def main(args: Array[String]): Unit = {
    val _ = Await.ready(Http.serve(":8080", service))
  }

}
