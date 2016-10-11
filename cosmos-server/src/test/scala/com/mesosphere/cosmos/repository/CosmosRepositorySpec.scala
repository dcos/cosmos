package com.mesosphere.cosmos.repository

import com.mesosphere.{cosmos => C}
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.universe
import com.netaporter.uri.Uri
import org.scalatest.FreeSpec
import com.twitter.util.{Await, Future, Return, Throw, Try}
import com.mesosphere.cosmos.test.TestUtil.Anonymous
import com.mesosphere.cosmos.PackageNotFound
import com.mesosphere.cosmos.VersionNotFound
import com.mesosphere.universe.test.TestingPackages

import scala.util.matching.Regex
import com.twitter.common.util.Clock


final class CosmosRepositorySpec extends FreeSpec {
  def client(ls: List[universe.v3.model.PackageDefinition]): C.repository.UniverseClient = {
    new C.repository.UniverseClient {
      def apply(repository: PackageRepository)(implicit session: RequestSession): Future[universe.v3.model.Repository] = Future {
        universe.v3.model.Repository(ls)
      }
    }
  }
  "getPackageByReleaseVersion" - {
    "not found" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(rep, client(Nil))
      val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.releaseVersion
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByReleaseVersion("test", ver))))
    }
    "found minimal" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(rep, client(List(TestingPackages.MinimalV3ModelV2PackageDefinition)))
      val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.releaseVersion
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByReleaseVersion("test", ver))))
      assertResult(Return(TestingPackages.MinimalV3ModelV2PackageDefinition))(Try(Await.result(c.getPackageByReleaseVersion("minimal", ver))))
    }
    "found MAXIMAL" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(rep, client(List(TestingPackages.MinimalV3ModelV2PackageDefinition, TestingPackages.MaximalV3ModelV3PackageDefinition)))
      val ver = TestingPackages.MaximalV3ModelV3PackageDefinition.releaseVersion
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByReleaseVersion("test", ver))))

      assertResult(Throw(new PackageNotFound("minimal")))(Try(Await.result(c.getPackageByReleaseVersion("minimal", ver))))
      assertResult(Return(TestingPackages.MaximalV3ModelV3PackageDefinition))(Try(Await.result(c.getPackageByReleaseVersion("MAXIMAL", ver))))
    }
  }
  "getPackageByPackageVersion" - {
    "not found" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(rep, client(Nil))
      val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.version
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByPackageVersion("test", Some(ver)))))
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByPackageVersion("test", None))))
    }
    "found minimal" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val c = CosmosRepository(rep, client(List(TestingPackages.MinimalV3ModelV2PackageDefinition)))
      val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.version
      assertResult(Return((TestingPackages.MinimalV3ModelV2PackageDefinition,u)))(Try(Await.result(c.getPackageByPackageVersion("minimal", Some(ver)))))
      assertResult(Return((TestingPackages.MinimalV3ModelV2PackageDefinition,u)))(Try(Await.result(c.getPackageByPackageVersion("minimal", None))))
      val bad = TestingPackages.MaximalV3ModelV3PackageDefinition.version
      assertResult(Throw(new VersionNotFound("minimal", bad)))(Try(Await.result(c.getPackageByPackageVersion("minimal", Some(bad)))))
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByPackageVersion("test", Some(ver)))))
    }
    "found MAXIMAL" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val c = CosmosRepository(rep, client(List(TestingPackages.MinimalV3ModelV2PackageDefinition, TestingPackages.MaximalV3ModelV3PackageDefinition)))
      val ver = TestingPackages.MaximalV3ModelV3PackageDefinition.version
      assertResult(Return((TestingPackages.MaximalV3ModelV3PackageDefinition,u)))(Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(ver)))))
      val bad = TestingPackages.MinimalV3ModelV2PackageDefinition.version
      assertResult(Throw(new VersionNotFound("MAXIMAL", bad)))(Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(bad)))))
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
      val c = CosmosRepository(rep, client(List(TestingPackages.MinimalV3ModelV2PackageDefinition, TestingPackages.MinimalV3ModelV2PackageDefinition)))
      assertResult(Return(List(TestingPackages.MinimalV3ModelV2PackageDefinition, TestingPackages.MinimalV3ModelV2PackageDefinition)))(Try(Await.result(c.getPackagesByPackageName("minimal"))))
      assertResult(Return(Nil))(Try(Await.result(c.getPackagesByPackageName("test"))))
    }
    "found MAXIMAL" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val c = CosmosRepository(rep, client(List(TestingPackages.MinimalV3ModelV2PackageDefinition, TestingPackages.MinimalV3ModelV2PackageDefinition, TestingPackages.MaximalV3ModelV3PackageDefinition)))

      assertResult(Return(List(TestingPackages.MinimalV3ModelV2PackageDefinition, TestingPackages.MinimalV3ModelV2PackageDefinition)))(Try(Await.result(c.getPackagesByPackageName("minimal"))))
      assertResult(Return(List(TestingPackages.MaximalV3ModelV3PackageDefinition)))(Try(Await.result(c.getPackagesByPackageName("MAXIMAL"))))
      assertResult(Return(Nil))(Try(Await.result(c.getPackagesByPackageName("test"))))
    }
  }
  "createRegex" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val c = new DefaultCosmosRepository(rep, client(List(TestingPackages.MinimalV3ModelV2PackageDefinition)))
      assertResult("^\\Qminimal\\E$")(c.createRegex("minimal").toString)
      assertResult("^\\Qmin\\E.*\\Qmal\\E$")(c.createRegex("min*mal").toString)
      assertResult("^\\Qmini\\E.*\\Q.+\\E$")(c.createRegex("mini*.+").toString)
      assertResult("^\\Qminimal\\E.*$")(c.createRegex("minimal*").toString)
      assertResult("^\\Qminimal\\E.*.*$")(c.createRegex("minimal**").toString)
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
    def client(before: List[universe.v3.model.PackageDefinition],
               after: List[universe.v3.model.PackageDefinition]
               ): C.repository.UniverseClient = {
      new C.repository.UniverseClient {
        def apply(repository: PackageRepository)
                 (implicit session: RequestSession)
                 :Future[universe.v3.model.Repository] = Future { 
          if (count == 0) {
            count = count + 1
            universe.v3.model.Repository(before)
          } else {
            universe.v3.model.Repository(after)
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
}
