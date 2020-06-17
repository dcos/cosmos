package com.mesosphere.cosmos

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.EndpointUriSyntax
import com.mesosphere.universe
import io.lemonlabs.uri.Uri
import io.lemonlabs.uri.dsl._
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.finagle.stats.StatsReceiver
import io.circe.jawn.parse
import org.scalatest.FreeSpec
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.{ExecutionContextExecutor, Future}

final class HttpClientSpec extends FreeSpec with ScalaFutures {

  implicit val system: ActorSystem = ActorSystem("http-client-test")
  implicit val mat: Materializer = ActorMaterializer()
  implicit lazy val ctx: ExecutionContextExecutor = system.dispatcher

  import HttpClientSpec._

  "HttpClient.fetch() retrieves the given URL" in {
    val future = HttpClient.fetch(JsonContentUrl) { responseData =>
      assertResult(universe.MediaTypes.UniverseV4Repository)(responseData.entity.contentType)

      val contentString = Unmarshal(responseData.entity).to[String].futureValue
      assert(parse(contentString).isRight)
      Future.successful(Done)
    }
    future.futureValue
  }

  "HttpClient.fetch() reports URL syntax problems" - {

    "relative URI" in {
      val cosmosException = intercept[CosmosException](
        HttpClient.fetch("foo/bar")(_ => Future.successful(Done)).futureValue
      )
      assert(cosmosException.error.isInstanceOf[EndpointUriSyntax])
    }

    "unknown protocol" in {
      val cosmosException = intercept[CosmosException](
        HttpClient.fetch("foo://bar.com")(_ => Future.successful(Done)).futureValue
      )
      assert(cosmosException.error.isInstanceOf[EndpointUriSyntax])
    }

    "URISyntaxException" in {
      val cosmosException = intercept[CosmosException](
        HttpClient.fetch("/\\")(_ => Future.successful(Done)).futureValue
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
