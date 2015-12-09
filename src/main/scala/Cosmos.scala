package com.mesosphere.cosmos

import com.twitter.finagle.http.exp.Multipart.FileUpload
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, Http}
import com.twitter.util.Await

import io.finch._

import shapeless.HNil

object Cosmos {

  val ping: Endpoint[String] = get("ping") { Ok("pong") }

  val importPath = "v1" / "package" / "import"
  val packageImport: Endpoint[String] = post(importPath ? safeFileUpload("file")) { _: FileUpload =>
    Ok("Import successful!\n")
  }

  val service: Service[Request, Response] = (ping :+: packageImport).toService

  def main(args: Array[String]): Unit = {
    Await.ready(Http.serve(":8080", service))
  }

}
