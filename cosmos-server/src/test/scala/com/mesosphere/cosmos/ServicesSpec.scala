package com.mesosphere.cosmos

import com.netaporter.uri.dsl._
import com.twitter.finagle.http._
import com.twitter.util.{Await, Return}
import org.scalatest.FreeSpec

final class ServicesSpec extends FreeSpec {

  "Services" - {

    "adminRouterClient should" - {
      "be able to be created with a well formed URI with a domain that doesn't resolve" in {
        val url = "http://some.domain.that-im-pretty-sure-doesnt-exist-anywhere"
        val Return(client) = Services.adminRouterClient(url)

        try {
          val request = RequestBuilder().url(url).buildGet()
          val response = Await.result(client(request))
          assertResult(response.status)(Status.ServiceUnavailable)
        } catch {
          case e: ServiceUnavailable => // Success
        } finally {
          val _ = Await.ready(client.close())
        }
      }
    }

  }
}
