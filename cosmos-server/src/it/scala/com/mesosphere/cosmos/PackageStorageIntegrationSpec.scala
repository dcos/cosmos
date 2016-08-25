package com.mesosphere.cosmos

import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import org.scalatest.FreeSpec
import com.mesosphere.cosmos.test.TestUtil._
import com.mesosphere.universe.v3.model._
import com.netaporter.uri.Uri

class PackageStorageIntegrationSpec extends FreeSpec {
  private[this] val cosmosClient = CosmosIntegrationTestClient.CosmosClient

  import PackageStorageIntegrationSpec._

  "The user must be able to" - {
    "publish a bundle, and the bundle must be in repo served by the storage" in {
      BundlePackagePairs.foreach { case (bundle, pkg) =>
        cosmosClient.packagePublish(PublishRequest(bundle))
        assert(cosmosClient.packageStorageRepository.packages.contains(pkg))
      }
    }

    "see the bundles published when listing packages" in {
      val act = cosmosClient.packageRepositoryAdd(
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
      cosmosClient.packagePublish(PublishRequest(uniqueBundle))
      val pkgNames = cosmosClient.packageSearch(
        SearchRequest(
          Some(uniquePackageName)
        )
      ).packages.map(_.name)

      assert(pkgNames.contains(uniquePackageName))
      assertResult(uniquePackageName)(pkgNames.head)

      val actr = cosmosClient.packageRepositoryDelete(
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
  private val uri = Uri.parse("http://localhost:7070/package/storage/repository")
  private val exp = PackageRepository(name, uri)
}
