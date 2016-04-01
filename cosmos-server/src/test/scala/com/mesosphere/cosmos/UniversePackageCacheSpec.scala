package com.mesosphere.cosmos

import java.io.{ByteArrayInputStream, IOException, InputStream}
import java.net.{MalformedURLException, URL}
import java.nio.file.{Files, Path, Paths}

import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.model.{PackageRepository, SearchResult}
import com.mesosphere.cosmos.test.TestUtil
import com.mesosphere.universe._
import com.netaporter.uri.Uri
import com.twitter.io.{Charsets, StreamIO}
import com.twitter.util.{Await, Throw}
import io.circe.syntax._
import org.scalatest.FreeSpec

final class UniversePackageCacheSpec extends FreeSpec {

  import UniversePackageCacheSpec._

  // TODO: This is not really a unit test because we have to use a temporary directory to
  // store inputs for the test. Refactor UniversePackageCache so that it uses a trait to access
  // package files, allowing this test to mock the trait. See issue #275.
  "Issue #270: include package resources in search results" - {

    "packageResources()" in {
      val bundleDir = Files.createTempDirectory(getClass.getSimpleName)
      initializePackageCache(bundleDir, List((SomeIndexEntry, SomeResource)))

      val repoDir = UniversePackageCache.repoDirectory(bundleDir)
      val universeIndex = UniverseIndex(SomeUniverseVersion, List(SomeIndexEntry))

      assertResult(SomeResource) {
        UniversePackageCache.packageResources(repoDir, universeIndex, SomeIndexEntry.name)
      }

      TestUtil.deleteRecursively(bundleDir)
    }

    "search()" in {
      val bundleDir = Files.createTempDirectory(getClass.getSimpleName)
      initializePackageCache(bundleDir, List((SomeIndexEntry, SomeResource)))

      val universeIndex = UniverseIndex(SomeUniverseVersion, List(SomeIndexEntry))

      val searchResult = SearchResult(
        SomeIndexEntry.name,
        SomeIndexEntry.currentVersion,
        SomeIndexEntry.versions,
        SomeIndexEntry.description,
        SomeIndexEntry.framework,
        SomeIndexEntry.tags,
        images = SomeResource.images
      )

      assertResult(List(searchResult)) {
        UniversePackageCache.search(bundleDir, universeIndex, queryOpt = None)
      }

      TestUtil.deleteRecursively(bundleDir)
    }

  }

  "streamBundle()" - {
    "URI/URL syntax" - {
      "relative URI" in {
        val expectedRepo = PackageRepository(name = "FooBar", uri = Uri.parse("foo/bar"))
        val Throw(RepositoryUriSyntax(actualRepo, causedBy)) =
          Await.result(UniversePackageCache.streamBundle(expectedRepo, _.openStream()).liftToTry)
        assertResult(expectedRepo)(actualRepo)
        assert(causedBy.isInstanceOf[IllegalArgumentException])
      }

      "unknown protocol" in {
        val expectedRepo = PackageRepository(name = "FooBar", uri = Uri.parse("foo://bar.com"))
        val Throw(RepositoryUriSyntax(actualRepo, causedBy)) =
          Await.result(UniversePackageCache.streamBundle(expectedRepo, _.openStream()).liftToTry)
        assertResult(expectedRepo)(actualRepo)
        assert(causedBy.isInstanceOf[MalformedURLException])
      }
    }

    "Connection failure" in {
      val expectedRepo = PackageRepository(name = "BadRepo", uri = Uri.parse("http://example.com"))
      val errorMessage = "No one's home"
      val streamUrl: URL => InputStream = _ => throw new IOException(errorMessage)
      val Throw(RepositoryUriConnection(actualRepo, causedBy)) =
        Await.result(UniversePackageCache.streamBundle(expectedRepo, streamUrl).liftToTry)
      assertResult(expectedRepo)(actualRepo)
      assert(causedBy.isInstanceOf[IOException])
      assertResult(errorMessage)(causedBy.getMessage)
    }

    "Connection success" in {
      val repository = PackageRepository(name = "GoodRepo", uri = Uri.parse("http://example.com"))
      val bundleContent = "Pretend this is a package repository zip file"
      val bundleStream = new ByteArrayInputStream(bundleContent.getBytes(Charsets.Utf8))
      val streamFuture = UniversePackageCache.streamBundle(repository, _ => bundleStream)
      val downloadStream = Await.result(streamFuture)
      val downloadContent = StreamIO.buffer(downloadStream).toString(Charsets.Utf8.name)
      assertResult(bundleContent)(downloadContent)
    }
  }

  "updateUniverseCache()" - {
    "uses streamBundle()" - {
      "error" in {
        val universeDir = Files.createTempDirectory(getClass.getSimpleName)

        val repository = PackageRepository(name = "BadRepo", uri = Uri.parse("http://example.com"))
        val bundleDirFuture = UniversePackageCache.updateUniverseCache(repository, universeDir) {
          _ => throw new IOException("No one's home")
        }
        val Throw(t) = Await.result(bundleDirFuture.liftToTry)
        assert(t.isInstanceOf[RepositoryUriConnection])

        TestUtil.deleteRecursively(universeDir)
      }

      "success" in {
        val universeDir = Files.createTempDirectory(getClass.getSimpleName)

        val repository = PackageRepository(name = "GoodRepo", uri = Uri.parse("http://example.com"))
        val bundleContent = "Not a zip file, but good enough to pass the test"
        val bundleStream = new ByteArrayInputStream(bundleContent.getBytes(Charsets.Utf8))
        val bundleDirFuture =
          UniversePackageCache.updateUniverseCache(repository, universeDir)(_ => bundleStream)
        assert(Await.result(bundleDirFuture.liftToTry).isReturn)

        TestUtil.deleteRecursively(universeDir)
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
