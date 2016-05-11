package com.mesosphere.cosmos

import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.handler.PackageSearchHandler
import com.mesosphere.cosmos.model.{SearchRequest, SearchResponse, SearchResult}
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.universe.{PackageDetailsVersion, ReleaseVersion}
import com.twitter.util.{Await, Future}
import io.finch.circe._
import org.mockito.Mockito._

final class PackageSearchHandlerSpec extends UnitSpec {

  import PackageSearchHandlerSpec._
  import com.mesosphere.cosmos.test.TestUtil.Anonymous

  "Search results are sorted by case-insensitive package name" in {
    val a = searchResult("a")
    val b = searchResult("B")
    val c = searchResult("cassandra")
    val d = searchResult("Distributed-Systems-Thingy")

    val packageCollection = mock[PackageCollection]
    when(packageCollection.search(None)).thenReturn(Future.value(List(c, b, d, a)))

    val handler = new PackageSearchHandler(packageCollection)

    assertResult(SearchResponse(Seq(a, b, c, d))) {
      Await.result(handler(SearchRequest(None)))
    }
  }

}

object PackageSearchHandlerSpec {

  def searchResult(name: String): SearchResult = {
    SearchResult(
      name = name,
      currentVersion = PackageDetailsVersion("1.2.3"),
      versions = Map(PackageDetailsVersion("1.2.3") -> ReleaseVersion("0")),
      description = "a package",
      tags = Nil
    )
  }

}
