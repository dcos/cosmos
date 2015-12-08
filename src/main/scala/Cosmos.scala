package com.mesosphere.cosmos

import com.twitter.finagle.http.{Request, Response}
import io.finch._
import com.twitter.finagle.{Service, Http}
import com.twitter.util.Await

object Cosmos {

  val ping: Endpoint[String] = get("ping") { Ok("pong") }

  val packageImport: Endpoint[String] = post("v1" / "package" / "import") {
    Ok("Import successful!\n")
  }

  val service: Service[Request, Response] = (ping :+: packageImport).toService

  def main(args: Array[String]): Unit = {
    Await.ready(Http.serve(":8080", service))
  }

}
