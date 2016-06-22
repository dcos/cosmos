package com.mesosphere.cosmos.repository

import java.io.IOException
import java.net.MalformedURLException
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.{RepositoryUriConnection, RepositoryUriSyntax}
import com.mesosphere.universe.v3.model.DcosReleaseVersion
import com.netaporter.uri.Uri
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
          val universeClient = UniverseClient(CosmosIntegrationTestClient.adminRouter)
          val Throw(RepositoryUriSyntax(actualRepo, causedBy)) =
            Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
          assertResult(expectedRepo)(actualRepo)
          assert(causedBy.isInstanceOf[IllegalArgumentException])
        }

        "unknown protocol" in {
          val expectedRepo = PackageRepository(name = "FooBar", uri = Uri.parse("foo://bar.com"))
          val universeClient = UniverseClient(CosmosIntegrationTestClient.adminRouter)
          val Throw(RepositoryUriSyntax(actualRepo, causedBy)) =
            Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
          assertResult(expectedRepo)(actualRepo)
          assert(causedBy.isInstanceOf[MalformedURLException])
        }
      }

      "Connection failure" in {
        val expectedRepo = PackageRepository(name = "BadRepo", uri = Uri.parse("http://foobar"))
        val universeClient = UniverseClient(CosmosIntegrationTestClient.adminRouter)
        val Throw(RepositoryUriConnection(actualRepo, causedBy)) =
          Await.result(universeClient(expectedRepo, version1Dot8).liftToTry)
        assertResult(expectedRepo)(actualRepo)
        assert(causedBy.isInstanceOf[IOException])
      }

    }

  }

}
