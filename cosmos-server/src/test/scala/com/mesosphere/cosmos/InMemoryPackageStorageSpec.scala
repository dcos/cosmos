package com.mesosphere.cosmos

import com.mesosphere.cosmos.storage.InMemoryPackageStorage
import com.mesosphere.cosmos.test.TestUtil._
import com.mesosphere.universe.v3.model._
import org.scalatest.FreeSpec
import com.twitter.util.Await

final class InMemoryPackageStorageSpec extends FreeSpec {

  "The repository must contain all published packages in any order" - {
    "when no packages have been uploaded" in {
      val storage = new InMemoryPackageStorage()
      val exp = Repository(List())
      val act = Await.result(storage.getRepository)
      assertResult(exp)(act)
    }

    "when a single package has been published" in {
      val storage = new InMemoryPackageStorage()
      val (bundle, pkg) = BundlePackagePairs.head
      val exp = Repository(List(pkg))
      val act = putBundleAndGetRepo(storage, bundle)
      assertResult(exp)(act)
    }

    "when multiple packages have been published" - {
      "with the condition that" - {
        "for all packages with the same name the release version ordering matches the publishing ordering" in {
          val storage = new InMemoryPackageStorage()
          BundlePackagePairs.foreach { case (bundle, pkg) =>
            val repo = putBundleAndGetRepo(storage, bundle)
            assert(repo.packages.contains(pkg))
          }

          val exp = BundlePackagePairs.map(_._2).sortBy(nameAndRelease)
          val act = Await.result(
            storage.getRepository
          ).packages.sortBy(nameAndRelease)
          assertResult(exp)(act)
        }
      }
    }
  }

  def putBundleAndGetRepo(storage: InMemoryPackageStorage, bundle: BundleDefinition): Repository = {
    Await.result(
      for {
        _ <- storage.putPackageBundle(bundle)
        r <- storage.getRepository
      } yield r
    )
  }
}
