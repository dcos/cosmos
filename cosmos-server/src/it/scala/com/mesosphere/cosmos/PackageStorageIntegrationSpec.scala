package com.mesosphere.cosmos

import com.mesosphere.cosmos.internal.model.V2Bundle
import com.mesosphere.cosmos.internal.model.V3Bundle
import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.test.TestUtil._
import com.netaporter.uri.Uri
import org.scalatest.FreeSpec

final class PackageStorageIntegrationSpec extends FreeSpec {

  import PackageStorageIntegrationSpec._

  "The user must be able to" - {
    "publish a bundle, and the bundle must be in repo served by the storage" in {
      BundlePackagePairs.foreach { case (bundle, pkg) =>
        CosmosClient.packagePublish(PublishRequest(bundle))
        assert(CosmosClient.packageStorageRepository.packages.contains(pkg))
      }
    }

    "see the bundles published when listing packages" in {
      val act = CosmosClient.packageRepositoryAdd(
        PackageRepositoryAddRequest(
          name,
          uri,
          Some(0)
        )
      ).repositories.head
      assertResult(exp)(act)

      val uniquePackageName = "ThisIsAUniquePackageNameInPackageStorageIntegrationSpec"
      val uniqueBundle = BundlePackagePairs.head._1 match {
        case v2: V2Bundle => v2.copy(name = uniquePackageName)
        case v3: V3Bundle => v3.copy(name = uniquePackageName)
      }
      CosmosClient.packagePublish(PublishRequest(uniqueBundle))
      val pkgNames = CosmosClient.packageSearch(
        SearchRequest(
          Some(uniquePackageName)
        )
      ).packages.map(_.name)

      assert(pkgNames.contains(uniquePackageName))
      assertResult(uniquePackageName)(pkgNames.head)

      val actr = CosmosClient.packageRepositoryDelete(
        PackageRepositoryDeleteRequest(
          Some(name), Some(uri)
        )
      ).repositories

      assert(!actr.contains(exp))
    }
  }

}

object PackageStorageIntegrationSpec {
  private val name = "InMemoryPackageStore"
  private val uri = Uri.parse(s"${CosmosClient.uri}/package/storage/repository")
  private val exp = PackageRepository(name, uri)
}
