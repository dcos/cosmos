package com.mesosphere.cosmos

import java.nio.file.Paths

import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.model.SearchResult
import com.mesosphere.universe._
import com.netaporter.uri.Uri
import com.twitter.io.Charsets
import com.twitter.util.Await
import io.circe.syntax._
import org.mockito.Mockito._
import org.scalatest.FreeSpec
import org.scalatest.mock.MockitoSugar

final class UniversePackageCacheSpec extends FreeSpec {

  import UniversePackageCacheSpec._

  "Issue #270: include package resources in search results" in {
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
      Await.result(SomeUniversePackageCache.search(queryOpt = None))
    }
  }

}

object UniversePackageCacheSpec extends MockitoSugar {

  private val SomeReleaseVersion = ReleaseVersion("1")
  private val SomeUniverseVersion = UniverseVersion("2.3.4")

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

  private val SomeUniversePackageCache: UniversePackageCache = {
    val universeIndex = UniverseIndex(SomeUniverseVersion, List(SomeIndexEntry))
    val indexPath = Paths.get("/repo/meta/index.json")
    val indexBytes = universeIndex.asJson.noSpaces.getBytes(Charsets.Utf8)
    val resourcePath = Paths.get("/repo/packages/S/somePackage/1/resource.json")
    val resourceBytes = SomeResource.asJson.noSpaces.getBytes(Charsets.Utf8)

    val packageMetadataStore = mock[PackageMetadataStore]
    when(packageMetadataStore.readFile(resourcePath)).thenReturn(Some(resourceBytes))
    when(packageMetadataStore.readFile(indexPath)).thenReturn(Some(indexBytes))

    UniversePackageCache(Uri.parse("/any/uri"), Paths.get("/"), packageMetadataStore)
  }

}
