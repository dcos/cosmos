package com.mesosphere.cosmos.repository

import com.mesosphere.{cosmos => C}
import com.mesosphere.cosmos
import com.mesosphere.{universe => U}
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.http.RequestSession
import com.netaporter.uri.Uri
import org.scalatest.FreeSpec
import org.scalatest.PrivateMethodTester._
import com.twitter.util.Future
import com.twitter.util.Await
import com.twitter.util.{Throw,Try,Return}
import com.mesosphere.cosmos.test.TestUtil
import com.mesosphere.cosmos.test.TestUtil._ //RequestSession implicit
import com.mesosphere.cosmos.PackageNotFound
import scala.util.matching.Regex
import java.util.concurrent.atomic.AtomicReference
import java.time.LocalDateTime

final class CosmosRepositorySpec extends FreeSpec {
  def client(ls: List[C.internal.model.PackageDefinition]): C.repository.UniverseClient = {
    new C.repository.UniverseClient {
      def apply(repository: PackageRepository)(implicit session: RequestSession): Future[C.internal.model.CosmosInternalRepository] = Future { 
        C.internal.model.CosmosInternalRepository(ls)
      }
    }
  }
  "getPackageByReleaseVersion" - {
    "not found" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(rep, client(Nil))
      val ver = TestUtil.MinimalPackageDefinition.releaseVersion
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByReleaseVersion("test", ver))))
    }
    "found minimal" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(rep, client(List(TestUtil.MinimalPackageDefinition)))
      val ver = TestUtil.MinimalPackageDefinition.releaseVersion
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByReleaseVersion("test", ver))))
      assertResult(Return(TestUtil.MinimalPackageDefinition))(Try(Await.result(c.getPackageByReleaseVersion("minimal", ver))))
    }
    "found MAXIMAL" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(rep, client(List(TestUtil.MinimalPackageDefinition, TestUtil.MaximalPackageDefinition)))
      val ver = TestUtil.MaximalPackageDefinition.releaseVersion
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByReleaseVersion("test", ver))))

      assertResult(Throw(new PackageNotFound("minimal")))(Try(Await.result(c.getPackageByReleaseVersion("minimal", ver))))
      assertResult(Return(TestUtil.MaximalPackageDefinition))(Try(Await.result(c.getPackageByReleaseVersion("MAXIMAL", ver))))
    }
  }
  "getPackageByPackageVersion" - {
    "not found" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(rep, client(Nil))
      val ver = TestUtil.MinimalPackageDefinition.version
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByPackageVersion("test", Some(ver)))))
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByPackageVersion("test", None))))
    }
    "found minimal" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val c = CosmosRepository(rep, client(List(TestUtil.MinimalPackageDefinition)))
      val ver = TestUtil.MinimalPackageDefinition.version
      assertResult(Return((TestUtil.MinimalPackageDefinition,u)))(Try(Await.result(c.getPackageByPackageVersion("minimal", Some(ver)))))
      assertResult(Return((TestUtil.MinimalPackageDefinition,u)))(Try(Await.result(c.getPackageByPackageVersion("minimal", None))))
      val bad = TestUtil.MaximalPackageDefinition.version
      assertResult(Throw(new PackageNotFound("minimal")))(Try(Await.result(c.getPackageByPackageVersion("minimal", Some(bad)))))
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByPackageVersion("test", Some(ver)))))
    }
    "found MAXIMAL" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val c = CosmosRepository(rep, client(List(TestUtil.MinimalPackageDefinition, TestUtil.MaximalPackageDefinition)))
      val ver = TestUtil.MaximalPackageDefinition.version
      assertResult(Return((TestUtil.MaximalPackageDefinition,u)))(Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(ver)))))
      val bad = TestUtil.MinimalPackageDefinition.version
      assertResult(Throw(new PackageNotFound("MAXIMAL")))(Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(bad)))))
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
      val c = CosmosRepository(rep, client(List(TestUtil.MinimalPackageDefinition)))
      assertResult(Return(List(TestUtil.MinimalPackageDefinition)))(Try(Await.result(c.getPackagesByPackageName("minimal"))))
      assertResult(Return(Nil))(Try(Await.result(c.getPackagesByPackageName("test"))))
    }
    "found MAXIMAL" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val c = CosmosRepository(rep, client(List(TestUtil.MinimalPackageDefinition, TestUtil.MinimalPackageDefinition, TestUtil.MaximalPackageDefinition)))

      assertResult(Return(List(TestUtil.MinimalPackageDefinition, TestUtil.MinimalPackageDefinition)))(Try(Await.result(c.getPackagesByPackageName("minimal"))))
      assertResult(Return(List(TestUtil.MaximalPackageDefinition)))(Try(Await.result(c.getPackagesByPackageName("MAXIMAL"))))
      assertResult(Return(Nil))(Try(Await.result(c.getPackagesByPackageName("test"))))
    }
  }
  "createRegex" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val c = CosmosRepository(rep, client(List(TestUtil.MinimalPackageDefinition)))
      val createRegex = PrivateMethod[Regex]('createRegex)
      assertResult("^\\Qminimal\\E$")((c invokePrivate createRegex("minimal")).toString)
      assertResult("^\\Qmin\\E.*\\Qmal\\E$")((c invokePrivate createRegex("min*mal")).toString)
      assertResult("^\\Qmini\\E.*\\Q.+\\E$")((c invokePrivate createRegex("mini*.+")).toString)
      assertResult("^\\Qminimal\\E.*$")((c invokePrivate createRegex("minimal*")).toString)
      assertResult("^\\Qminimal\\E.*.*$")((c invokePrivate createRegex("minimal**")).toString)
  }
  "search" - {
    "not found" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val c = CosmosRepository(rep, client(List(TestUtil.MinimalPackageDefinition)))
      assertResult(Return(Nil))(Try(Await.result(c.search(Some("test")))))
      assertResult(Return(Nil))(Try(Await.result(c.search(Some("mini*.+")))))
    }

    "all" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val all = List(TestUtil.MaximalPackageDefinition,TestUtil.MinimalPackageDefinition)
      val c = CosmosRepository(rep, client(all))
      assertResult(2)(Try(Await.result(c.search(None))).get.length)
    }
    "found" in {
      val u = Uri.parse("/uri")
      val rep = C.rpc.v1.model.PackageRepository("test", u)
      val l = List(TestUtil.MinimalPackageDefinition)
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
      val l = List(TestUtil.MaximalPackageDefinition)
      val c = CosmosRepository(rep, client(l))
      assertResult("MAXIMAL")(Try(Await.result(c.search(Some("all")))).get.head.name)
      assertResult("MAXIMAL")(Try(Await.result(c.search(Some("thing*")))).get.head.name)
    }
  }
  "timeout repository" in {
    var count = 0
    def client(before: List[C.internal.model.PackageDefinition],
               after: List[C.internal.model.PackageDefinition]): C.repository.UniverseClient = {
      new C.repository.UniverseClient {
        def apply(repository: PackageRepository)(implicit session: RequestSession): Future[C.internal.model.CosmosInternalRepository] = Future { 
          if (count == 0) {
            count = count + 1
            C.internal.model.CosmosInternalRepository(before)
          } else {
            C.internal.model.CosmosInternalRepository(after)
          }
        }
      }
    }
    val u = Uri.parse("/uri")
    val rep = C.rpc.v1.model.PackageRepository("test", u)
    val a = List(TestUtil.MaximalPackageDefinition)
    val b = List(TestUtil.MinimalPackageDefinition)
    val c = CosmosRepository(rep, client(b, a))
    assertResult(Return(Nil))(Try(Await.result(c.search(Some("MAXIMAL")))))

    val getrep = PrivateMethod[AtomicReference[Option[(C.internal.model.CosmosInternalRepository, LocalDateTime)]]]('lastRepository)
    (c invokePrivate getrep()).set(
      Some((C.internal.model.CosmosInternalRepository(b), LocalDateTime.now().plusMinutes(-2)))
    )
    assertResult("MAXIMAL")(Try(Await.result(c.search(Some("MAXIMAL")))).get.head.name)
  }
}
