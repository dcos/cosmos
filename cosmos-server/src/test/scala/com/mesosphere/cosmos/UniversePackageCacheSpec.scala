package com.mesosphere.cosmos

import org.scalatest.{FreeSpec, PrivateMethodTester}

final class UniversePackageCacheSpec extends FreeSpec with PrivateMethodTester {

  // TODO(version): See if this test can be rewritten for UniversePackageCache's replacement
  // TODO: This is not really a unit test because we have to use a temporary directory to
  // store inputs for the test. Refactor UniversePackageCache so that it uses a trait to access
  // package files, allowing this test to mock the trait. See issue #275.
  //  "Issue #270: include package resources in search results" - {
  //
  //    "packageResources()" in {
  //      val bundleDir = Files.createTempDirectory(getClass.getSimpleName)
  //      initializePackageCache(bundleDir, List((SomeIndexEntry, SomeResource)))
  //
  //      val repoDir = UniversePackageCache.repoDirectory(bundleDir)
  //      val universeIndex = UniverseIndex(SomeUniverseVersion, List(SomeIndexEntry))
  //
  //      assertResult(SomeResource) {
  //        UniversePackageCache.packageResources(repoDir, universeIndex, SomeIndexEntry.name)
  //      }
  //
  //      TestUtil.deleteRecursively(bundleDir)
  //    }
  //
  //    "search()" in {
  //      val bundleDir = Files.createTempDirectory(getClass.getSimpleName)
  //      initializePackageCache(bundleDir, List((SomeIndexEntry, SomeResource)))
  //
  //      val universeIndex = UniverseIndex(SomeUniverseVersion, List(SomeIndexEntry))
  //
  //      val searchResult = SearchResult(
  //        SomeIndexEntry.name,
  //        SomeIndexEntry.currentVersion,
  //        SomeIndexEntry.versions,
  //        SomeIndexEntry.description,
  //        SomeIndexEntry.framework,
  //        SomeIndexEntry.tags,
  //        images = SomeResource.images
  //      )
  //
  //      assertResult(List(searchResult)) {
  //        UniversePackageCache.search(bundleDir, universeIndex, queryOpt = None)
  //      }
  //
  //      TestUtil.deleteRecursively(bundleDir)
  //    }
  //
  //  }

}
