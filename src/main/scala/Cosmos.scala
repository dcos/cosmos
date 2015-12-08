package com.mesosphere.cosmos

import io.finch._
import com.twitter.finagle.Http
import com.twitter.util.Await

object Cosmos {

  val ping: Endpoint[String] = get("ping") { Ok("pong") }

  def main(args: Array[String]): Unit = {
    Await.ready(Http.serve(":8080", ping.toService))
  }
}
