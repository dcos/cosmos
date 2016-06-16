package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.universe.v3.model.DcosReleaseVersionParser
import com.mesosphere.universe.v3.model.PackageDefinition.Version
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.util.Await
import org.scalatest.FreeSpec

class UniverseRepositoryFetcherSpec extends FreeSpec {

  val fetcher = UniverseClient()

  import CosmosIntegrationTestClient.Session

  val baseRepoUri: Uri = "https://downloads.mesosphere.com/universe/dce867e9af73b85172d5a36bf8114c69b3be024e"

  "UniverseRepositoryFetcher should" - {
    "be able to fetch" - {
      "1.8 json" in {
        val version = DcosReleaseVersionParser.parseUnsafe("1.8-dev")
        val repo = Await.result(fetcher(baseRepoUri / "repo-up-to-1.8.json", version))
        val cassVersions = repo.packages
          .filter(_.name == "cassandra")
          .sorted
          .map(_.version)

        assertResult(List(
          Version("0.2.0-1"),
          Version("0.2.0-2")
        ))(cassVersions)
      }
      "1.7 json" in {
        val version = DcosReleaseVersionParser.parseUnsafe("1.7")
        val repo = Await.result(fetcher(baseRepoUri / "repo-empty-v3.json", version))
        assert(repo.packages.isEmpty)
      }
      "1.6.1 zip" in {
        val version = DcosReleaseVersionParser.parseUnsafe("1.6.1")
        val repo = Await.result(fetcher(baseRepoUri / "repo-up-to-1.6.1.zip", version))
        assert(repo.packages.nonEmpty)
      }
    }
  }
}
