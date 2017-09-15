package com.mesosphere.cosmos

import _root_.io.circe.jawn.parse
import _root_.io.circe.syntax._
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.twitter.finagle.http.Status
import org.scalatest.FreeSpec
import org.scalatest.Matchers

class ListVersionsSpec extends FreeSpec with Matchers {

  "ListVersionHandler should" - {
    "list only helloworld versions found in the first repo with helloworld packages" in {
      val request = CosmosRequests.packageListVersions(
        rpc.v1.model.ListVersionsRequest("helloworld", includePackageVersions = true))
      val response = CosmosClient.submit(request)
      val expectedContent = Map(
        "results" -> Map(
          "0.4.2" -> "5",
          "0.4.1" -> "4",
          "0.4.0" -> "3",
          "0.1.0" -> "0"
        )
      ).asJson
      response.status shouldBe Status.Ok
      parse(response.contentString) shouldBe
        Right(expectedContent)
    }
  }
}
