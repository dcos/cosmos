package com.mesosphere.cosmos

import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Await
import io.circe.jawn.parse
import org.scalatest.FreeSpec
import scala.io.Source

final class HttpClientSpec extends FreeSpec {

  import HttpClientSpec._

  "HttpClient.fetch() retrieves the given URL" in {
    val future = HttpClient.fetch(JsonContentUrl, stats) { responseData =>
      assertResult(universe.MediaTypes.UniverseV4Repository)(responseData.contentType)

      val contentString = Source.fromInputStream(responseData.contentStream).mkString
      assert(parse(contentString).isRight)
    }

    Await.result(future)
  }

  "HttpClient.fetch() reports URL syntax problems" - {

    "relative URI" in {
      val Left(error) = Await.result(HttpClient.fetch("foo/bar", stats)(_ => ()))
      assert(error.isInstanceOf[HttpClient.UriSyntax])
    }

    "unknown protocol" in {
      val Left(error) = Await.result(HttpClient.fetch("foo://bar.com", stats)(_ => ()))
      assert(error.isInstanceOf[HttpClient.UriSyntax])
    }

    "URISyntaxException" in {
      val Left(error) = Await.result(HttpClient.fetch("/\\", stats)(_ => ()))
      assert(error.isInstanceOf[HttpClient.UriSyntax])
    }

  }

}

object HttpClientSpec {

  val stats: StatsReceiver = NullStatsReceiver

  // TODO proxy Use ItObjects.V4TestUniverse once Jose's PR is merged
  val JsonContentUrl: Uri = "https://downloads.mesosphere.com/universe/" +
    "ae6a07ac0b53924154add2cd61403c5233272d93/repo/repo-up-to-1.10.json"

}
