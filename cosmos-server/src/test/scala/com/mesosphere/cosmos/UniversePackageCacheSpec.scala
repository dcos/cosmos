package com.mesosphere.cosmos

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException}
import java.net.MalformedURLException
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}
import com.netaporter.uri.Uri
import com.twitter.io.{Charsets, StreamIO}
import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.Throw
import io.circe.syntax._
import org.scalatest.{FreeSpec, PrivateMethodTester}
import com.mesosphere.cosmos.model.{PackageRepository, SearchResult}
import com.mesosphere.cosmos.repository.UniverseClient
import com.mesosphere.cosmos.test.TestUtil
import com.mesosphere.universe.v2.circe.Encoders._
import com.mesosphere.universe.v2.model._

final class UniversePackageCacheSpec extends FreeSpec with PrivateMethodTester {

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
          Await.result(UniversePackageCache.streamBundle(expectedRepo, UniverseClient()).liftToTry)
        assertResult(expectedRepo)(actualRepo)
        assert(causedBy.isInstanceOf[IllegalArgumentException])
      }

      "unknown protocol" in {
        val expectedRepo = PackageRepository(name = "FooBar", uri = Uri.parse("foo://bar.com"))
        val Throw(RepositoryUriSyntax(actualRepo, causedBy)) =
          Await.result(UniversePackageCache.streamBundle(expectedRepo, UniverseClient()).liftToTry)
        assertResult(expectedRepo)(actualRepo)
        assert(causedBy.isInstanceOf[MalformedURLException])
      }
    }

    "Connection failure" in {
      val expectedRepo = PackageRepository(name = "BadRepo", uri = Uri.parse("http://example.com"))
      val errorMessage = "No one's home"
      val universeClient = UniverseClient(_ => Future(throw new IOException(errorMessage)))
      val Throw(RepositoryUriConnection(actualRepo, causedBy)) =
        Await.result(UniversePackageCache.streamBundle(expectedRepo, universeClient).liftToTry)
      assertResult(expectedRepo)(actualRepo)
      assert(causedBy.isInstanceOf[IOException])
      assertResult(errorMessage)(causedBy.getMessage)
    }

    "Connection success" in {
      val repository = PackageRepository(name = "GoodRepo", uri = Uri.parse("http://example.com"))
      val bundleContent = "Pretend this is a package repository zip file"
      val bundleStream = new ByteArrayInputStream(bundleContent.getBytes(Charsets.Utf8))
      val streamFuture = UniversePackageCache.streamBundle(
        repository,
        UniverseClient(_ => Future(bundleStream))
      )
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
        val bundleDirFuture = UniversePackageCache.updateUniverseCache(
          repository,
          universeDir,
          UniverseClient(_ => Future(throw new IOException("No one's home")))
        )
        val Throw(t) = Await.result(bundleDirFuture.liftToTry)
        assert(t.isInstanceOf[RepositoryUriConnection])

        TestUtil.deleteRecursively(universeDir)
      }

      "success" in {
        val universeDir = Files.createTempDirectory(getClass.getSimpleName)

        val repository = PackageRepository(name = "GoodRepo", uri = Uri.parse("http://example.com"))
        val bundleContent = "Not a zip file, but good enough to pass the test"
        val bundleStream = new ByteArrayInputStream(bundleContent.getBytes(Charsets.Utf8))
        val bundleDirFuture = UniversePackageCache.updateUniverseCache(
          repository,
          universeDir,
          UniverseClient(_ => Future(bundleStream))
        )
        assert(Await.result(bundleDirFuture.liftToTry).isReturn)

        TestUtil.deleteRecursively(universeDir)
      }
    }
  }

  "Issue #250: create parent directories for unzipped files" - {
    "extractBundleToDirectory()" in {
      val tempDir = Files.createTempDirectory(getClass.getSimpleName)

      val extractBundleToDirectory = PrivateMethod[Unit]('extractBundleToDirectory)
      val bundle = createBundle

      // No exceptions will be thrown if this is successful
      UniversePackageCache invokePrivate extractBundleToDirectory(bundle, tempDir)

      assertExtractedFiles(tempDir)

      TestUtil.deleteRecursively(tempDir)
    }

    "extractBundle()" in {
      val universeDir = Files.createTempDirectory(getClass.getSimpleName)

      val extractBundle = PrivateMethod[Path]('extractBundle)
      val bundle = createBundle

      // No exceptions will be thrown if this is successful
      val repoDir =
        UniversePackageCache invokePrivate extractBundle(bundle, Uri.parse("repo/uri"), universeDir)

      assertExtractedFiles(repoDir)

      TestUtil.deleteRecursively(universeDir)
    }

    def createBundle: ZipInputStream = {
      val baos = new ByteArrayOutputStream
      val zipOut = new ZipOutputStream(baos)
      val firstEntry = new ZipEntry("bar/baz.txt")
      val secondEntry = new ZipEntry("foo/bar/baz.txt")
      val fileBytes = "hello world".getBytes(Charsets.Utf8)

      zipOut.putNextEntry(firstEntry)
      zipOut.write(fileBytes, 0, fileBytes.length)
      zipOut.closeEntry()

      zipOut.putNextEntry(secondEntry)
      zipOut.write(fileBytes, 0, fileBytes.length)
      zipOut.closeEntry()

      zipOut.finish()
      zipOut.close()

      val bais = new ByteArrayInputStream(baos.toByteArray)
      new ZipInputStream(bais)
    }

    def assertExtractedFiles(targetDir: Path): Unit = {
      val firstBytes = Files.readAllBytes(targetDir.resolve("baz.txt"))
      val secondBytes = Files.readAllBytes(targetDir.resolve("bar/baz.txt"))
      assertResult("hello world")(new String(firstBytes, Charsets.Utf8))
      assertResult("hello world")(new String(secondBytes, Charsets.Utf8))
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
