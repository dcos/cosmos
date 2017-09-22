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
    val future = HttpClient.fetch(JsonContentUrl) { responseData =>
      assertResult(universe.MediaTypes.UniverseV4Repository)(responseData.contentType)

      val contentString = Source.fromInputStream(responseData.contentStream).mkString
      assert(parse(contentString).isRight)
    }

    Await.result(future)
  }

  "HttpClient.fetch() reports URL syntax problems" - {

    "relative URI" in {
      val Left(error) = Await.result(HttpClient.fetch("foo/bar")(_ => ()))
      assert(error.isInstanceOf[HttpClient.UriSyntax])
    }

    "unknown protocol" in {
      val Left(error) = Await.result(HttpClient.fetch("foo://bar.com")(_ => ()))
      assert(error.isInstanceOf[HttpClient.UriSyntax])
    }

    "URISyntaxException" in {
      val Left(error) = Await.result(HttpClient.fetch("/\\")(_ => ()))
      assert(error.isInstanceOf[HttpClient.UriSyntax])
    }

  }

}

object HttpClientSpec {
  implicit val stats: StatsReceiver = NullStatsReceiver
  val JsonContentUrl: Uri = "https://downloads.mesosphere.com/universe/" +
    "ae6a07ac0b53924154add2cd61403c5233272d93/repo/repo-up-to-1.10.json"
}
