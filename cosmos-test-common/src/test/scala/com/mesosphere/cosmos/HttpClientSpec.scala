package com.mesosphere.cosmos

import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.EndpointUriSyntax
import com.mesosphere.universe
import io.lemonlabs.uri.Uri
import io.lemonlabs.uri.dsl._
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
      val cosmosException = intercept[CosmosException](
        Await.result(HttpClient.fetch("foo/bar")(_ => ()))
      )
      assert(cosmosException.error.isInstanceOf[EndpointUriSyntax])
    }

    "unknown protocol" in {
      val cosmosException = intercept[CosmosException](
        Await.result(HttpClient.fetch("foo://bar.com")(_ => ()))
      )
      assert(cosmosException.error.isInstanceOf[EndpointUriSyntax])
    }

    "URISyntaxException" in {
      val cosmosException = intercept[CosmosException](
        Await.result(HttpClient.fetch("/\\")(_ => ()))
      )
      assert(cosmosException.error.isInstanceOf[EndpointUriSyntax])
    }

  }

}

object HttpClientSpec {
  implicit val stats: StatsReceiver = NullStatsReceiver
  val JsonContentUrl: Uri = "https://downloads.mesosphere.com/universe/" +
    "ae6a07ac0b53924154add2cd61403c5233272d93/repo/repo-up-to-1.10.json"
}
