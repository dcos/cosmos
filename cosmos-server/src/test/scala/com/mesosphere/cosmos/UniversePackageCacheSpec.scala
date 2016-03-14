package com.mesosphere.cosmos

import java.nio.file.{Files, Path, Paths}

import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.model.SearchResult
import com.mesosphere.universe._
import com.twitter.io.Charsets
import io.circe.syntax._
import org.scalatest.{BeforeAndAfterAll, FreeSpec}

final class UniversePackageCacheSpec extends FreeSpec with BeforeAndAfterAll {

  import UniversePackageCacheSpec._

  // TODO: This is not really a unit test because we have to use a temporary directory to
  // store inputs for the test. Refactor UniversePackageCache so that it uses a trait to access
  // package files, allowing this test to mock the trait. See issue #275.
  var bundleDir: Path = _

  override def beforeAll(): Unit = {
    bundleDir = Files.createTempDirectory(getClass.getSimpleName)
    initializePackageCache(bundleDir, List((SomeIndexEntry, SomeResource)))
  }

  override def afterAll(): Unit = {
    assert(com.twitter.io.Files.delete(bundleDir.toFile))
  }

  "Issue #270: include package resources in search results" - {

    "packageResources()" in {
      val repoDir = UniversePackageCache.repoDirectory(bundleDir)
      val universeIndex = UniverseIndex(SomeUniverseVersion, List(SomeIndexEntry))

      assertResult(SomeResource) {
        UniversePackageCache.packageResources(repoDir, universeIndex, SomeIndexEntry.name)
      }
    }

    "search()" in {
      val universeIndex = UniverseIndex(SomeUniverseVersion, List(SomeIndexEntry))

      val searchResult = SearchResult(
        SomeIndexEntry.name,
        SomeIndexEntry.currentVersion,
        SomeIndexEntry.versions,
        SomeIndexEntry.description,
        SomeIndexEntry.framework,
        SomeIndexEntry.tags,
        SomeResource.images
      )

      assertResult(List(searchResult)) {
        UniversePackageCache.search(bundleDir, universeIndex, queryOpt = None)
      }
    }

  }

}

object UniversePackageCacheSpec {

  private val SomeReleaseVersion = ReleaseVersion("1")
  private val SomeUniverseVersion = UniverseVersion("4.5.6")

  private val SomeIndexEntry: UniverseIndexEntry = {
    val currentVersion = PackageDetailsVersion("1.2.3")

    UniverseIndexEntry(
      name = "somePackage",
      currentVersion = currentVersion,
      versions = Map(currentVersion -> SomeReleaseVersion),
      description = "does not matter",
      tags = Nil
    )
  }

  private val SomeResource: Resource = {
    val assets = Assets(
      uris = Some(Map("foo" -> "bar")),
      container = Some(Container(Map("key" -> "value")))
    )

    Resource(
      assets = Some(assets),
      images = Some(Images("small", "medium", "large", screenshots = Some(List("screenshot"))))
    )
  }

  private def initializePackageCache(
    bundleDir: Path,
    packageInfo: List[(UniverseIndexEntry, Resource)]
  ): Unit = {
    val indexEntries = packageInfo.map(_._1)
    val universeIndex = UniverseIndex(SomeUniverseVersion, indexEntries)
    val indexJson = universeIndex.asJson.noSpaces.getBytes(Charsets.Utf8)
    val metaDir = bundleDir.resolve(Paths.get("repo", "meta"))
    Files.createDirectories(metaDir)
    Files.write(metaDir.resolve("index.json"), indexJson)

    packageInfo.foreach { case (indexEntry, resource) =>
      val resourceJson = resource.asJson.noSpaces.getBytes(Charsets.Utf8)
      val packageName = indexEntry.name
      val packageFirstLetter = packageName.charAt(0).toUpper.toString
      val packagePath =
        Paths.get("repo", "packages", packageFirstLetter, packageName, SomeReleaseVersion.toString)
      val packageDir = bundleDir.resolve(packagePath)
      Files.createDirectories(packageDir)
      Files.write(packageDir.resolve("resource.json"), resourceJson)
    }
  }

}
