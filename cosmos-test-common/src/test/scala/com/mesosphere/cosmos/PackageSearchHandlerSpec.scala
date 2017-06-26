package com.mesosphere.cosmos

import com.mesosphere.cosmos.handler.PackageSearchHandler
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.rpc.v1.model.SearchRequest
import com.mesosphere.cosmos.rpc.v1.model.SearchResponse
import com.mesosphere.cosmos.rpc.v1.model.SearchResult
import com.mesosphere.universe
import com.twitter.util.Await
import com.twitter.util.Future
import org.mockito.Mockito._
import org.scalatest.FreeSpec
import org.scalatest.mockito.MockitoSugar

final class PackageSearchHandlerSpec extends FreeSpec with MockitoSugar {

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
      currentVersion = universe.v3.model.Version("1.2.3"),
      versions = Map(
        universe.v3.model.Version("1.2.3") ->
          universe.v3.model.ReleaseVersion(0)
      ),
      description = "a package",
      tags = Nil,
      framework = false
    )
  }

}
