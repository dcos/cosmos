package com.mesosphere.cosmos

import com.mesosphere.cosmos.internal.model.PackageDefinition
import com.mesosphere.cosmos.internal.model.CosmosInternalRepository
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.repository.UniverseClient
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import org.scalatest.FreeSpec
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.test.TestUtil
import com.mesosphere.cosmos.test.TestUtil.Anonymous //RequestSession implicit
import com.twitter.util.{Future,Await,Throw,Try,Return}
import com.netaporter.uri.Uri
import cats.data.Ior
import cats.data.Ior.{Left,Right,Both}

final class MultiRepositorySpec extends FreeSpec {

  case class TestClient(repos: List[PackageRepository] = Nil, ls: List[PackageDefinition] = Nil) extends UniverseClient {
    def apply(repo: PackageRepository)(implicit session: RequestSession): Future[CosmosInternalRepository] = Future { 
      val rv = repos.filter( _ == repo).flatMap(_ => ls)
      rv match {
        case Nil => throw PackageNotFound("foobar")
        case ls => CosmosInternalRepository(ls)
      }
    }
  }
  case class TestStorage(initial:List[PackageRepository] = Nil) extends PackageSourcesStorage {
    var cache: List[PackageRepository] = initial
    def read(): Future[List[PackageRepository]] =  Future { cache }

    def readCache(): Future[List[PackageRepository]] =  Future { cache }

    def add(index: Option[Int], packageRepository: PackageRepository): Future[List[PackageRepository]] = sys.error("add")

    def delete(nameOrUri: Ior[String, Uri]): Future[List[PackageRepository]] =  sys.error("delete")
  }
  "getPackagesByPackageName" - {
    "not found" in {
      val c = new MultiRepository(TestStorage(), TestClient())
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackagesByPackageName("test"))))
    }
    "found minimal" in {
      val repos = List(PackageRepository("minimal", Uri.parse("/test")))
      val storage = TestStorage(repos)
      val cls = List(TestUtil.MinimalPackageDefinition)
      val client = TestClient(repos, cls)
      val c = new MultiRepository(storage, client)
      assertResult(Return(cls))(Try(Await.result(c.getPackagesByPackageName("minimal"))))
    }
    "invalid repo" in {
      val invalid = List(PackageRepository("invalid", Uri.parse("/invalid")))
      val repos = List(PackageRepository("minimal", Uri.parse("/test")))
      val storage = TestStorage(repos)
      val cls = List(TestUtil.MinimalPackageDefinition)
      val client = TestClient(invalid, cls)
      val c = new MultiRepository(storage, client)
      assertResult(Throw(new PackageNotFound("minimal")))(Try(Await.result(c.getPackagesByPackageName("minimal"))))
    }
    "wrong query" in {
      val repos = List(PackageRepository("valid", Uri.parse("/valid")))
      val storage = TestStorage(repos)
      val cls = List(TestUtil.MinimalPackageDefinition)
      val client = TestClient(repos, cls)
      val c = new MultiRepository(storage, client)
      assertResult(Throw(new PackageNotFound("MAXIMAL")))(Try(Await.result(c.getPackagesByPackageName("MAXIMAL"))))
    }
    "from many" in {
      val repos = List(PackageRepository("valid", Uri.parse("/valid")))
      val storage = TestStorage(repos)
      val cls = List(TestUtil.MaximalPackageDefinition, TestUtil.MinimalPackageDefinition)
      val client = TestClient(repos, cls)
      val c = new MultiRepository(storage, client)
      val expect = List(TestUtil.MinimalPackageDefinition)
      assertResult(Return(expect))(Try(Await.result(c.getPackagesByPackageName("minimal"))))
    }
  }
  "getPackageByPackageVersion" - {
    "not found" in {
      val c = new MultiRepository(TestStorage(), TestClient())
      val ver = TestUtil.MinimalPackageDefinition.version
      assertResult(Throw(new VersionNotFound("test", ver)))(Try(Await.result(c.getPackageByPackageVersion("test", Some(ver)))))
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByPackageVersion("test", None))))
    }
    "found minimal" in {
      val u = Uri.parse("/test")
      val repos = List(PackageRepository("minimal", u))
      val storage = TestStorage(repos)
      val cls = List(TestUtil.MinimalPackageDefinition)
      val client = TestClient(repos, cls)
      val c = new MultiRepository(storage, client)
      val ver = TestUtil.MinimalPackageDefinition.version
      assertResult(Return((cls.head, u)))(Try(Await.result(c.getPackageByPackageVersion("minimal", None))))
      assertResult(Return((cls.head, u)))(Try(Await.result(c.getPackageByPackageVersion("minimal", Some(ver)))))
    }
    "invalid repo" in {
      val invalid = List(PackageRepository("invalid", Uri.parse("/invalid")))
      val repos = List(PackageRepository("minimal", Uri.parse("/test")))
      val storage = TestStorage(repos)
      val cls = List(TestUtil.MinimalPackageDefinition)
      val client = TestClient(invalid, cls)
      val c = new MultiRepository(storage, client)
      val ver = TestUtil.MinimalPackageDefinition.version
      assertResult(Throw(new PackageNotFound("minimal")))(Try(Await.result(c.getPackageByPackageVersion("minimal", None))))
      assertResult(Throw(new VersionNotFound("minimal",ver)))(Try(Await.result(c.getPackageByPackageVersion("minimal", Some(ver)))))
    }
    "wrong query" in {
      val repos = List(PackageRepository("valid", Uri.parse("/valid")))
      val storage = TestStorage(repos)
      val cls = List(TestUtil.MinimalPackageDefinition)
      val client = TestClient(repos, cls)
      val c = new MultiRepository(storage, client)
      val ver = TestUtil.MinimalPackageDefinition.version
      val badver = TestUtil.MaximalPackageDefinition.version
      assertResult(Throw(new PackageNotFound("MAXIMAL")))(Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", None))))
      assertResult(Throw(new VersionNotFound("MAXIMAL", ver)))(Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(ver)))))
      assertResult(Throw(new VersionNotFound("minimal", badver)))(Try(Await.result(c.getPackageByPackageVersion("minimal", Some(badver)))))
    }
    "from many" in {
      val u = Uri.parse("/valid")
      val repos = List(PackageRepository("valid", u))
      val storage = TestStorage(repos)
      val cls = List(TestUtil.MaximalPackageDefinition, TestUtil.MinimalPackageDefinition)
      val client = TestClient(repos, cls)
      val c = new MultiRepository(storage, client)
      val minver = TestUtil.MinimalPackageDefinition.version
      val minexp = (TestUtil.MinimalPackageDefinition, u)
      val maxver = TestUtil.MaximalPackageDefinition.version

      assertResult(Return(minexp))(Try(Await.result(c.getPackageByPackageVersion("minimal", None))))
      assertResult(Return(minexp))(Try(Await.result(c.getPackageByPackageVersion("minimal", Some(minver)))))
      assertResult(Throw(new VersionNotFound("minimal", maxver)))(Try(Await.result(c.getPackageByPackageVersion("minimal", Some(maxver)))))

      val maxexp = (TestUtil.MaximalPackageDefinition, u)
      assertResult(Return(maxexp))(Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", None))))
      assertResult(Return(maxexp))(Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(maxver)))))
      assertResult(Throw(new VersionNotFound("MAXIMAL", minver)))(Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(minver)))))
    }
  }
}
