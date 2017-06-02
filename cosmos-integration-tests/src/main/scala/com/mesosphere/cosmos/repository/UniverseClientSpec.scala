package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.CosmosException
import com.mesosphere.cosmos.GenericHttpError
import com.mesosphere.cosmos.RepositoryUriConnection
import com.mesosphere.cosmos.RepositoryUriSyntax
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.universe.v3.model.DcosReleaseVersion
import com.mesosphere.universe.v3.model.DcosReleaseVersionParser
import com.mesosphere.universe.v3.model.Repository
import com.mesosphere.universe.v3.model.Version
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.http.Status
import com.twitter.util.Await
import com.twitter.util.Throw
import java.io.IOException
import java.net.MalformedURLException
import org.scalatest.FreeSpec
import org.scalatest.Matchers

final class UniverseClientSpec extends FreeSpec with Matchers {

  "UniverseClient" - {

    val universeClient = new DefaultUniverseClient(CosmosIntegrationTestClient.adminRouter)

    val version1Dot8 = {
      val (major, minor) = (1, 8)
      DcosReleaseVersion(DcosReleaseVersion.Version(major), List(DcosReleaseVersion.Version(minor)))
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
          val Throw(CosmosException(RepositoryUriSyntax(actualRepo, _), _, _, Some(causedBy))) =
            Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
          assertResult(expectedRepo)(actualRepo)
          assert(causedBy.isInstanceOf[IllegalArgumentException])
        }

        "unknown protocol" in {
          val expectedRepo = PackageRepository(name = "FooBar", uri = Uri.parse("foo://bar.com"))
          val Throw(CosmosException(RepositoryUriSyntax(actualRepo, _), _, _, Some(causedBy))) =
            Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
          assertResult(expectedRepo)(actualRepo)
          assert(causedBy.isInstanceOf[MalformedURLException])
        }
      }

      "Connection failure" in {
        val expectedRepo = PackageRepository(name = "BadRepo", uri = Uri.parse("http://foobar"))
        val Throw(CosmosException(RepositoryUriConnection(actualRepo, _), _, _, Some(causedBy))) =
          Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
        assertResult(expectedRepo)(actualRepo)
        assert(causedBy.isInstanceOf[IOException])
      }

    }

    "should be able to fetch" - {

      "1.10 json" in {
        val version = DcosReleaseVersionParser.parseUnsafe("1.10")
        val repoFilename = "repo-up-to-1.10.json"
        val repository = v4Repository(repoFilename)
        val repo = Await.result(universeClient(repository, version))
        getVersions(repo, "helloworld") shouldBe
          List(Version("0.4.0"), Version("0.4.1"))
      }

      "1.8 json" in {
        val version = DcosReleaseVersionParser.parseUnsafe("1.8-dev")
        val repo = Await.result(universeClient(repository("repo-up-to-1.8.json"), version))
        assertResult(List(
          Version("0.2.0-1"),
          Version("0.2.0-2")
        ))(
          getVersions(repo, "cassandra")
        )
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
      val Throw(
        CosmosException(GenericHttpError(method, uri, clientStatus), status, _, _)
      ) = Await.result(
        result.liftToTry
      )

      assertResult("GET")(method.getName)
      assertResult(repoUri)(uri)
      assertResult(Status.Forbidden)(clientStatus)
      assertResult(Status.InternalServerError)(status)
    }

  }

  private[this] def getVersions(repository: Repository, name: String): List[Version] = {
    repository.packages
      .filter(_.name == name)
      .sorted
      .map(_.version)
  }

}
