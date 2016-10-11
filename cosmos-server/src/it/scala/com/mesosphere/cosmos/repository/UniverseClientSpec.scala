package com.mesosphere.cosmos.repository

import java.io.IOException
import java.net.MalformedURLException

import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.{GenericHttpError, RepositoryUriConnection, RepositoryUriSyntax}
import com.mesosphere.universe.v3.model.PackageDefinition.Version
import com.mesosphere.universe.v3.model.{DcosReleaseVersion, DcosReleaseVersionParser}
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.util.{Await, Throw}
import org.scalatest.FreeSpec

final class UniverseClientSpec extends FreeSpec {

  "UniverseClient" - {

    val universeClient = new DefaultUniverseClient(CosmosIntegrationTestClient.adminRouter)

    val version1Dot8 =
      DcosReleaseVersion(DcosReleaseVersion.Version(1), List(DcosReleaseVersion.Version(8)))

    val baseRepoUri: Uri = "https://downloads.mesosphere.com/universe/dce867e9af73b85172d5a36bf8114c69b3be024e"

    def repository(repoFilename: String): PackageRepository = {
      PackageRepository("repo", baseRepoUri / repoFilename)
    }

    "apply()" - {
      "URI/URL syntax" - {
        "relative URI" in {
          val expectedRepo = PackageRepository(name = "FooBar", uri = Uri.parse("foo/bar"))
          val Throw(RepositoryUriSyntax(actualRepo, causedBy)) =
            Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
          assertResult(expectedRepo)(actualRepo)
          assert(causedBy.isInstanceOf[IllegalArgumentException])
        }

        "unknown protocol" in {
          val expectedRepo = PackageRepository(name = "FooBar", uri = Uri.parse("foo://bar.com"))
          val Throw(RepositoryUriSyntax(actualRepo, causedBy)) =
            Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
          assertResult(expectedRepo)(actualRepo)
          assert(causedBy.isInstanceOf[MalformedURLException])
        }
      }

      "Connection failure" in {
        val expectedRepo = PackageRepository(name = "BadRepo", uri = Uri.parse("http://foobar"))
        val Throw(RepositoryUriConnection(actualRepo, causedBy)) =
          Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
        assertResult(expectedRepo)(actualRepo)
        assert(causedBy.isInstanceOf[IOException])
      }

    }

    "should be able to fetch" - {

      "1.8 json" in {
        val version = DcosReleaseVersionParser.parseUnsafe("1.8-dev")
        val repo = Await.result(universeClient(repository("repo-up-to-1.8.json"), version))
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
        val repo = Await.result(universeClient(repository("repo-empty-v3.json"), version))
        assert(repo.packages.isEmpty)
      }

      "1.6.1 zip" in {
        val version = DcosReleaseVersionParser.parseUnsafe("1.6.1")
        val repo = Await.result(universeClient(repository("repo-up-to-1.6.1.zip"), version))
        assert(repo.packages.nonEmpty)
      }

      "1.7 zip" in {
        val version = DcosReleaseVersionParser.parseUnsafe("1.7")
        val repo = Await.result(universeClient(repository("repo-up-to-1.7.zip"), version))
        assert(repo.packages.nonEmpty)
      }

    }

    "should fail to fetch a nonexistent repo file" in {
      val version = DcosReleaseVersionParser.parseUnsafe("0.0")
      val repoUri = baseRepoUri / "doesnotexist.json"
      val result = universeClient(PackageRepository("badRepo", repoUri), version)
      val Throw(GenericHttpError(method, uri, status)) = Await.result(result.liftToTry)

      assertResult("GET")(method.getName)
      assertResult(repoUri)(uri)
      assertResult(403)(status.code)
    }

  }

}
