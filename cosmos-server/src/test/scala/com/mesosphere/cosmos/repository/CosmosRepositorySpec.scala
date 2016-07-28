package com.mesosphere.cosmos.repository

import com.mesosphere.{cosmos => C}
import com.mesosphere.cosmos
import com.mesosphere.{universe => U}
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.http.RequestSession
import com.netaporter.uri.Uri
import org.scalatest.FreeSpec
import com.twitter.util.Future
import com.twitter.util.Await
import com.twitter.util.{Throw,Try,Return}
import com.mesosphere.cosmos.test.TestUtil
import com.mesosphere.cosmos.test.TestUtil._ //RequestSession implicit
import com.mesosphere.cosmos.PackageNotFound

final class CosmosRepositorySpec extends FreeSpec {
  def client(ls: List[C.internal.model.PackageDefinition]): C.repository.UniverseClient = {
    new C.repository.UniverseClient {
      def apply(repository: PackageRepository)(implicit session: RequestSession): Future[C.internal.model.CosmosInternalRepository] = Future { 
        C.internal.model.CosmosInternalRepository(ls)
      }
    }
  }
  "getPackageByReleaseVersion" - {
    "notFound" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(rep, client(Nil))
      val ver = TestUtil.MinimalPackageDefinition.releaseVersion
      assertResult(Throw(new PackageNotFound("test")))(Try(Await.result(c.getPackageByReleaseVersion("test", ver))))
    }
    "found minimal" in {
      val rep = C.rpc.v1.model.PackageRepository("test", Uri.parse("uri"))
      val c = CosmosRepository(rep, client(List(TestUtil.MinimalPackageDefinition)))
      val ver = TestUtil.MinimalPackageDefinition.releaseVersion
      assertResult(Return(TestUtil.MinimalPackageDefinition))(Try(Await.result(c.getPackageByReleaseVersion("minimal", ver))))
    }
  }
}
