package com.mesosphere.cosmos.repository

import java.io.IOException
import java.net.MalformedURLException

import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.{RepositoryUriConnection, RepositoryUriSyntax}
import com.mesosphere.universe.v3.model.PackageDefinition.Version
import com.mesosphere.universe.v3.model.{DcosReleaseVersion, DcosReleaseVersionParser}
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.util.{Await, Throw}
import org.scalatest.FreeSpec

final class UniverseClientSpec extends FreeSpec {

  "UniverseClient" - {

    val version1Dot8 =
      DcosReleaseVersion(DcosReleaseVersion.Version(1), List(DcosReleaseVersion.Version(8)))

    "apply()" - {
      "URI/URL syntax" - {
        "relative URI" in {
          val expectedRepo = PackageRepository(name = "FooBar", uri = Uri.parse("foo/bar"))
          val universeClient = new DefaultUniverseClient(CosmosIntegrationTestClient.adminRouter)
          val Throw(RepositoryUriSyntax(actualRepo, causedBy)) =
            Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
          assertResult(expectedRepo)(actualRepo)
          assert(causedBy.isInstanceOf[IllegalArgumentException])
        }

        "unknown protocol" in {
          val expectedRepo = PackageRepository(name = "FooBar", uri = Uri.parse("foo://bar.com"))
          val universeClient = new DefaultUniverseClient(CosmosIntegrationTestClient.adminRouter)
          val Throw(RepositoryUriSyntax(actualRepo, causedBy)) =
            Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
          assertResult(expectedRepo)(actualRepo)
          assert(causedBy.isInstanceOf[MalformedURLException])
        }
      }

      "Connection failure" in {
        val expectedRepo = PackageRepository(name = "BadRepo", uri = Uri.parse("http://foobar"))
        val universeClient = new DefaultUniverseClient(CosmosIntegrationTestClient.adminRouter)
        val Throw(RepositoryUriConnection(actualRepo, causedBy)) =
          Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
        assertResult(expectedRepo)(actualRepo)
        assert(causedBy.isInstanceOf[IOException])
      }

    }

    "should be able to fetch" - {

      val fetcher = new DefaultUniverseClient(CosmosIntegrationTestClient.adminRouter)

      val baseRepoUri: Uri = "https://downloads.mesosphere.com/universe/dce867e9af73b85172d5a36bf8114c69b3be024e"

      def repository(repoFilename: String): PackageRepository = {
        PackageRepository("repo", baseRepoUri / repoFilename)
      }

      "1.8 json" in {
        val version = DcosReleaseVersionParser.parseUnsafe("1.8-dev")
        val repo = Await.result(fetcher(repository("repo-up-to-1.8.json"), version))
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
        val repo = Await.result(fetcher(repository("repo-empty-v3.json"), version))
        assert(repo.packages.isEmpty)
      }

      "1.6.1 zip" in {
        val version = DcosReleaseVersionParser.parseUnsafe("1.6.1")
        val repo = Await.result(fetcher(repository("repo-up-to-1.6.1.zip"), version))
        assert(repo.packages.nonEmpty)
      }

      "1.7 zip" in {
        val version = DcosReleaseVersionParser.parseUnsafe("1.7")
        val repo = Await.result(fetcher(repository("repo-up-to-1.7.zip"), version))
        assert(repo.packages.nonEmpty)
      }

    }

  }

}
