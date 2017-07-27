/*
package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.error.PackageNotFound
import com.mesosphere.cosmos.error.VersionNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.test.TestUtil.Anonymous
import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.mesosphere.{cosmos => C}
import com.netaporter.uri.Uri
import com.twitter.common.util.Clock
import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

final class CosmosRepositorySpec extends FreeSpec with Matchers with TableDrivenPropertyChecks {
  def client(ls: List[universe.v4.model.PackageDefinition]): C.repository.UniverseClient = {
    new C.repository.UniverseClient {
      def apply(
        repository: PackageRepository
      )(
        implicit session: RequestSession
      ): Future[universe.v4.model.Repository] = Future {
        universe.v4.model.Repository(ls)
      }
    }
  }
  "getPackageByReleaseVersion" - {
    "not found" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(rep, client(Nil))
      val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.releaseVersion
      Try(Await.result(c.getPackageByReleaseVersion("test", ver))) shouldBe Throw(
        PackageNotFound("test").exception
      )
    }
    "found minimal" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(
        rep,
        client(List(TestingPackages.MinimalV3ModelV2PackageDefinition))
      )
      val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.releaseVersion
      Try(Await.result(c.getPackageByReleaseVersion("test", ver))) shouldBe Throw(
        PackageNotFound("test").exception
      )
      Try(Await.result(c.getPackageByReleaseVersion("minimal", ver))) shouldBe Return(
        TestingPackages.MinimalV3ModelV2PackageDefinition
      )
    }
    "found MAXIMAL" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(
        rep,
        client(
          List(
            TestingPackages.MinimalV3ModelV2PackageDefinition,
            TestingPackages.MaximalV3ModelV3PackageDefinition
          )
        )
      )
      val ver = TestingPackages.MaximalV3ModelV3PackageDefinition.releaseVersion
      Try(Await.result(c.getPackageByReleaseVersion("test", ver))) shouldBe Throw(
        PackageNotFound("test").exception
      )

      Try(Await.result(c.getPackageByReleaseVersion("minimal", ver))) shouldBe Throw(
        PackageNotFound("minimal").exception
      )
      Try(Await.result(c.getPackageByReleaseVersion("MAXIMAL", ver))) shouldBe Return(
        TestingPackages.MaximalV3ModelV3PackageDefinition
      )
    }

    "for all versions" in {
      forAll(TestingPackages.packageDefinitions) { packageDefinition =>
        val cosmosRepository = singletonRepository(Uri.parse("uri"), packageDefinition)
        val version = packageDefinition.releaseVersion
        val name = packageDefinition.name
        val actualPackage = Await.result(
          cosmosRepository.getPackageByReleaseVersion(name, version))
        actualPackage shouldBe packageDefinition
      }
    }
  }
  "getPackageByPackageVersion" - {
    "not found" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(rep, client(Nil))
      val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.version
      Try(Await.result(c.getPackageByPackageVersion("test", Some(ver)))) shouldBe Throw(
        PackageNotFound("test").exception
      )
      Try(Await.result(c.getPackageByPackageVersion("test", None))) shouldBe Throw(
        PackageNotFound("test").exception
      )
    }
    "found minimal" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val c = CosmosRepository(
        rep,
        client(List(TestingPackages.MinimalV3ModelV2PackageDefinition))
      )
      val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.version
      Try(Await.result(c.getPackageByPackageVersion("minimal", Some(ver)))) shouldBe Return(
        (TestingPackages.MinimalV3ModelV2PackageDefinition, u)
      )
      Try(Await.result(c.getPackageByPackageVersion("minimal", None))) shouldBe Return(
        (TestingPackages.MinimalV3ModelV2PackageDefinition, u)
      )

      val bad = TestingPackages.MaximalV3ModelV3PackageDefinition.version
      Try(Await.result(c.getPackageByPackageVersion("minimal", Some(bad)))) shouldBe Throw(
        VersionNotFound("minimal", bad).exception
      )
      Try(Await.result(c.getPackageByPackageVersion("test", Some(ver)))) shouldBe Throw(
        PackageNotFound("test").exception
      )
    }
    "found MAXIMAL" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val c = CosmosRepository(
        rep,
        client(
          List(
            TestingPackages.MinimalV3ModelV2PackageDefinition,
            TestingPackages.MaximalV3ModelV3PackageDefinition
          )
        )
      )
      val ver = TestingPackages.MaximalV3ModelV3PackageDefinition.version
      Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(ver)))) shouldBe Return(
        (TestingPackages.MaximalV3ModelV3PackageDefinition, u)
      )
      val bad = TestingPackages.MinimalV3ModelV2PackageDefinition.version
      Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(bad)))) shouldBe Throw(
        VersionNotFound("MAXIMAL", bad).exception
      )
    }
    "for all versions" in {
      forAll(TestingPackages.packageDefinitions) { packageDefinition =>
        val uri = Uri.parse("/uri")
        val cosmosRepository = singletonRepository(uri, packageDefinition)
        val version = packageDefinition.version
        val name = packageDefinition.name
        val actual = Await.result(
          cosmosRepository.getPackageByPackageVersion(name, Some(version))
        )
        actual shouldBe ((packageDefinition, uri))
      }
    }
  }

  "getPackagesByPackageName" - {
    "not found" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(rep, client(Nil))
      assertResult(Return(Nil))(Try(Await.result(c.getPackagesByPackageName("test"))))
    }
    "found minimal" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val c = CosmosRepository(rep, client(List(TestingPackages.MinimalV3ModelV2PackageDefinition)))
      assertResult(Return(List(TestingPackages.MinimalV3ModelV2PackageDefinition)))(Try(Await.result(c.getPackagesByPackageName("minimal"))))
      assertResult(Return(Nil))(Try(Await.result(c.getPackagesByPackageName("test"))))
    }

    "found multiple" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val packages = List(
        TestingPackages.MinimalV3ModelV2PackageDefinition,
        TestingPackages.MinimalV3ModelV2PackageDefinition
      )
      val c = CosmosRepository(rep, client(packages))

      assertResult(Return(packages))(Try(Await.result(c.getPackagesByPackageName("minimal"))))
      assertResult(Return(Nil))(Try(Await.result(c.getPackagesByPackageName("test"))))
    }
    "found MAXIMAL" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val minimal = List(
        TestingPackages.MinimalV3ModelV2PackageDefinition,
        TestingPackages.MinimalV3ModelV2PackageDefinition
      )
      val packages = TestingPackages.MaximalV3ModelV3PackageDefinition :: minimal
      val c = CosmosRepository(rep, client(packages))

      assertResult(Return(minimal))(Try(Await.result(c.getPackagesByPackageName("minimal"))))
      assertResult(Return(List(TestingPackages.MaximalV3ModelV3PackageDefinition))) {
        Try(Await.result(c.getPackagesByPackageName("MAXIMAL")))
      }
      assertResult(Return(Nil))(Try(Await.result(c.getPackagesByPackageName("test"))))
    }
    "for all versions" in {
      forAll(TestingPackages.packageDefinitions) { packageDefinition =>
        val uri = Uri.parse("/uri")
        val cosmosRepository = singletonRepository(uri, packageDefinition)
        val name = packageDefinition.name
        val actual = Await.result(
          cosmosRepository.getPackagesByPackageName(name)
        )
        actual shouldBe List(packageDefinition)
      }
    }
  }
  "createRegex" in {
      assertResult("^\\Qminimal\\E$")(CosmosRepository.createRegex("minimal").toString)
      assertResult("^\\Qmin\\E.*\\Qmal\\E$")(CosmosRepository.createRegex("min*mal").toString)
      assertResult("^\\Qmini\\E.*\\Q.+\\E$")(CosmosRepository.createRegex("mini*.+").toString)
      assertResult("^\\Qminimal\\E.*$")(CosmosRepository.createRegex("minimal*").toString)
      assertResult("^\\Qminimal\\E.*.*$")(CosmosRepository.createRegex("minimal**").toString)
  }
  "search" - {
    "not found" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val c = CosmosRepository(rep, client(List(TestingPackages.MinimalV3ModelV2PackageDefinition)))
      assertResult(Return(Nil))(Try(Await.result(c.search(Some("test")))))
      assertResult(Return(Nil))(Try(Await.result(c.search(Some("mini*.+")))))
    }

    "all" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val all = List(TestingPackages.MaximalV3ModelV3PackageDefinition,TestingPackages.MinimalV3ModelV2PackageDefinition)
      val c = CosmosRepository(rep, client(all))
      assertResult(2)(Try(Await.result(c.search(None))).get.length)
    }
    "found" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val l = List(TestingPackages.MinimalV3ModelV2PackageDefinition)
      val c = CosmosRepository(rep, client(l))
      assertResult("minimal")(Try(Await.result(c.search(Some("minimal")))).get.head.name)
      assertResult("minimal")(Try(Await.result(c.search(Some("mini*mal")))).get.head.name)
      assertResult("minimal")(Try(Await.result(c.search(Some("min*mal")))).get.head.name)
      assertResult("minimal")(Try(Await.result(c.search(Some("minimal*")))).get.head.name)
      assertResult("minimal")(Try(Await.result(c.search(Some("*minimal")))).get.head.name)
      assertResult("minimal")(Try(Await.result(c.search(Some("*minimal*")))).get.head.name)
      assertResult("minimal")(Try(Await.result(c.search(Some("*inimal")))).get.head.name)
      assertResult("minimal")(Try(Await.result(c.search(Some("minima*")))).get.head.name)
      assertResult("minimal")(Try(Await.result(c.search(Some("minima**")))).get.head.name)
      assertResult("minimal")(Try(Await.result(c.search(Some("**minimal")))).get.head.name)
      assertResult("minimal")(Try(Await.result(c.search(Some("**minimal**")))).get.head.name)
      assertResult("minimal")(Try(Await.result(c.search(Some("**mi**mal**")))).get.head.name)
    }
    "by tag" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val l = List(TestingPackages.MaximalV3ModelV3PackageDefinition)
      val c = CosmosRepository(rep, client(l))
      assertResult("MAXIMAL")(Try(Await.result(c.search(Some("all")))).get.head.name)
      assertResult("MAXIMAL")(Try(Await.result(c.search(Some("thing*")))).get.head.name)
    }
  }
  "timeout repository" in {
    var count = 0
    def client(before: List[universe.v4.model.PackageDefinition],
               after: List[universe.v4.model.PackageDefinition]
               ): C.repository.UniverseClient = {
      new C.repository.UniverseClient {
        def apply(
          repository: PackageRepository
        )(
          implicit session: RequestSession
        ): Future[universe.v4.model.Repository] = Future {
          if (count == 0) {
            count = count + 1
            universe.v4.model.Repository(before)
          } else {
            universe.v4.model.Repository(after)
          }
        }
      }
    }
    val u = Uri.parse("/uri")
    val rep = C.rpc.v1.model.PackageRepository("test", u)
    val a = List(TestingPackages.MaximalV3ModelV3PackageDefinition)
    val b = List(TestingPackages.MinimalV3ModelV2PackageDefinition)
    var millis: Long = 0
    val clock = new Clock {
      def nowMillis(): Long = millis
      def nowNanos(): Long = millis * 1000
      def waitFor(x:Long): Unit = sys.error("unexpected")
    }
    val c = CosmosRepository(rep, client(b, a), clock)
    assertResult("minimal")(Try(Await.result(c.search(Some("minimal")))).get.head.name)
    assertResult(Return(Nil))(Try(Await.result(c.search(Some("MAXIMAL")))))

    millis = millis + 60*1000*1000
    assertResult("MAXIMAL")(Try(Await.result(c.search(Some("MAXIMAL")))).get.head.name)

    //timer wrap-around
    count = 0
    assertResult(Return(Nil))(Try(Await.result(c.search(Some("minimal")))))
    millis = 0
    assertResult("minimal")(Try(Await.result(c.search(Some("minimal")))).get.head.name)
  }

  private[this] def singletonRepository(
    uri: Uri,
    packageDefinition: universe.v4.model.PackageDefinition
  ): CosmosRepository = {
    val packageRepository = C.rpc.v1.model.PackageRepository("test", uri)
    CosmosRepository(packageRepository, client(List(packageDefinition)))
  }
}
*/
