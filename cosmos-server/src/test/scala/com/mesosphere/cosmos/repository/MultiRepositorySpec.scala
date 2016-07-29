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
      CosmosInternalRepository(repos.filter( _ == repo).flatMap(_ => ls))
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
  }
}
