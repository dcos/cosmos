package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.RepositoryUriConnection
import com.mesosphere.cosmos.error.RepositoryUriSyntax
import com.mesosphere.cosmos.error.UniverseClientHttpError
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.util.Await
import com.twitter.util.Throw
import io.netty.handler.codec.http.HttpResponseStatus
import java.io.IOException
import java.net.MalformedURLException
import org.scalatest.FreeSpec
import org.scalatest.Matchers

final class UniverseClientSpec extends FreeSpec with Matchers {

  "UniverseClient" - {

    val universeClient = new DefaultUniverseClient(CosmosIntegrationTestClient.adminRouter)

    val version1Dot8 = {
      val (major, minor) = (1, 8)
      universe.v3.model.DcosReleaseVersion(
        universe.v3.model.DcosReleaseVersion.Version(major),
        List(universe.v3.model.DcosReleaseVersion.Version(minor))
      )
    }

    val baseRepoUri: Uri = "https://downloads.mesosphere.com/universe/dce867e9af73b85172d5a36bf8114c69b3be024e"

    def repository(repoFilename: String): PackageRepository = {
      PackageRepository("repo", baseRepoUri / repoFilename)
    }

    def v4Repository(repoFilename: String): PackageRepository = {
      val baseRepoUri = "https://downloads.mesosphere.com/universe/ebdcd8b7522e37f33184d343ae2a02ad0b63903b/repo"
      PackageRepository("repo", baseRepoUri / repoFilename)
    }

    "apply()" - {
      "URI/URL syntax" - {
        "relative URI" in {
          val expectedRepo = PackageRepository(name = "FooBar", uri = Uri.parse("foo/bar"))
          val Throw(CosmosException(RepositoryUriSyntax(actualRepo, _), _, Some(causedBy))) =
            Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
          assertResult(expectedRepo)(actualRepo)
          assert(causedBy.isInstanceOf[IllegalArgumentException])
        }

        "unknown protocol" in {
          val expectedRepo = PackageRepository(name = "FooBar", uri = Uri.parse("foo://bar.com"))
          val Throw(CosmosException(RepositoryUriSyntax(actualRepo, _), _, Some(causedBy))) =
            Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
          assertResult(expectedRepo)(actualRepo)
          assert(causedBy.isInstanceOf[MalformedURLException])
        }
      }

      "Connection failure" in {
        val expectedRepo = PackageRepository(name = "BadRepo", uri = Uri.parse("http://foobar"))
        val Throw(CosmosException(RepositoryUriConnection(actualRepo, _), _, Some(causedBy))) =
          Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
        assertResult(expectedRepo)(actualRepo)
        assert(causedBy.isInstanceOf[IOException])
      }

    }

    "should be able to fetch" - {

      "1.10 json" in {
        val version = universe.v3.model.DcosReleaseVersionParser.parseUnsafe("1.10")
        val repoFilename = "repo-up-to-1.10.json"
        val repository = v4Repository(repoFilename)
        val repo = Await.result(universeClient(repository, version))
        getVersions(repo, "helloworld") shouldBe
          List(universe.v3.model.Version("0.4.0"), universe.v3.model.Version("0.4.1"))
      }

      "1.8 json" in {
        val version = universe.v3.model.DcosReleaseVersionParser.parseUnsafe("1.8-dev")
        val repo = Await.result(universeClient(repository("repo-up-to-1.8.json"), version))
        assertResult(List(
          universe.v3.model.Version("0.2.0-1"),
          universe.v3.model.Version("0.2.0-2")
        ))(
          getVersions(repo, "cassandra")
        )
      }

      "1.7 json" in {
        val version = universe.v3.model.DcosReleaseVersionParser.parseUnsafe("1.7")
        val repo = Await.result(universeClient(repository("repo-empty-v3.json"), version))
        assert(repo.packages.isEmpty)
      }

      "1.6.1 zip" in {
        val version = universe.v3.model.DcosReleaseVersionParser.parseUnsafe("1.6.1")
        val repo = Await.result(universeClient(repository("repo-up-to-1.6.1.zip"), version))
        assert(repo.packages.nonEmpty)
      }

      "1.7 zip" in {
        val version = universe.v3.model.DcosReleaseVersionParser.parseUnsafe("1.7")
        val repo = Await.result(universeClient(repository("repo-up-to-1.7.zip"), version))
        assert(repo.packages.nonEmpty)
      }

    }

    "should fail to fetch a nonexistent repo file" in {
      val version = universe.v3.model.DcosReleaseVersionParser.parseUnsafe("0.0")
      val repoUri = baseRepoUri / "doesnotexist.json"
      val expectedPkgRepo = PackageRepository("badRepo", repoUri)
      val result = universeClient(expectedPkgRepo, version)
      val Throw(
        CosmosException(UniverseClientHttpError(actualPkgRepo, method, clientStatus, status), _, _)
      ) = Await.result(
        result.liftToTry
      )
      assertResult("GET")(method.getName)
      assertResult(expectedPkgRepo)(actualPkgRepo)
      assertResult(HttpResponseStatus.FORBIDDEN)(clientStatus)
      assertResult(HttpResponseStatus.INTERNAL_SERVER_ERROR)(status)
    }

  }

  private[this] def getVersions(
    repository: universe.v4.model.Repository,
    name: String
  ): List[universe.v3.model.Version] = {
    repository.packages
      .filter(_.name == name)
      .sorted
      .map(_.version)
  }

}
