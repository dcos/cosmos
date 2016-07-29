package com.mesosphere.cosmos

import com.mesosphere.cosmos.internal.model.PackageDefinition
import com.mesosphere.cosmos.internal.model.CosmosInternalRepository
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.repository.UniverseClient
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import org.scalatest.FreeSpec
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.test.TestUtil.Anonymous //RequestSession implicit
import com.twitter.util.{Future,Await,Throw,Try,Return}
import com.netaporter.uri.Uri
import cats.data.Ior
import cats.data.Ior.{Left,Right,Both}

final class MultiRepositorySpec extends FreeSpec {

  case class TestClient(ls: List[PackageDefinition] = Nil) extends UniverseClient {
    def apply(repository: PackageRepository)(implicit session: RequestSession): Future[CosmosInternalRepository] = Future { 
      CosmosInternalRepository(ls)
    }
  }
  case class TestStorage(initial:List[PackageRepository] = Nil) extends PackageSourcesStorage {
    var cache: List[PackageRepository] = initial
    def read(): Future[List[PackageRepository]] =  Future { cache }

    def readCache(): Future[List[PackageRepository]] =  Future { cache }

    def add(index: Option[Int], packageRepository: PackageRepository): Future[List[PackageRepository]] = {
      val pos = (index orElse Some(0)).get
      val (before, after) = cache.splitAt(pos)
      cache = before ++ (packageRepository :: after)
      Future { cache }
    }
    def delete(nameOrUri: Ior[String, Uri]): Future[List[PackageRepository]] =  {
      cache = cache.filter { pr => 
        val PackageRepository(name, uri) = pr
        nameOrUri match {
          case (Left(n))    => !(name == n)
          case (Right(u))   => !(uri == u)
          case (Both(n,u))  => !(uri == u && name == n)
        }
      }
      Future { cache }
    }
  }
  "getPackagesByPackageName" - {
    "not found" in {
      val c = new MultiRepository(TestStorage(), TestClient())
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackagesByPackageName("test"))))
    }
  }
}
