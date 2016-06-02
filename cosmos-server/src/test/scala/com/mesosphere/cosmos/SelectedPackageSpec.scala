package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.handler.PackageSearchHandler
import com.mesosphere.cosmos.model.{SearchRequest, SearchResponse, SearchResult}
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.universe.v2._
import com.mesosphere.universe.v2.circe.Decoders._
import com.mesosphere.universe.v2.circe.Encoders._
import com.twitter.util.{Await, Future}
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Printer}
import io.finch.circe._
import org.mockito.Mockito._

final class SelectedPackageSpec extends UnitSpec {

  import SelectedPackageSpec._
  import com.mesosphere.cosmos.test.TestUtil.Anonymous

  "selected packages appear first in search results" - {
    "with same package name" in {
      val selectedResult = makeSearchResult(selected = Some(true))
      val ordinaryResult = makeSearchResult(selected = Some(false))
      val anotherOrdinaryResult = makeSearchResult(selected = None)

      val beforeSorting = List(ordinaryResult, selectedResult, anotherOrdinaryResult)
      val afterSorting = List(selectedResult, ordinaryResult, anotherOrdinaryResult)
      assertSearchResults(beforeSorting, afterSorting)
    }

    "with sorted package names" in {
      val first = makeSearchResult(selected = Some(true), name = "antelope")
      val second = makeSearchResult(selected = Some(true), name = "xylocarp")
      val third = makeSearchResult(selected = Some(false), name = "aardvark")
      val fourth = makeSearchResult(selected = Some(false), name = "zebra")

      val beforeSorting = List(fourth, second, first, third)
      val afterSorting = List(first, second, third, fourth)
      assertSearchResults(beforeSorting, afterSorting)
    }

    def assertSearchResults(
      resultsBeforeSorting: List[SearchResult],
      resultsAfterSorting: List[SearchResult]
    ): Unit = {
      val packageCollection = mock[PackageCollection]
      when(packageCollection.search(None)).thenReturn {
        Future.value(resultsBeforeSorting)
      }
      val handler = new PackageSearchHandler(packageCollection)

      assertResult(SearchResponse(resultsAfterSorting)) {
        Await.result(handler(SearchRequest(None)))
      }
    }
  }

  "The selected fields from the index are carried through to search results" - {
    "UniversePackageCache.searchResult" - {
      "selected=true" in {
        assertSelectedPropagated(Some(true))
      }

      "selected=false" in {
        assertSelectedPropagated(Some(false))
      }

      "selected=None" in {
        assertSelectedPropagated(None)
      }

      def assertSelectedPropagated(selected: Option[Boolean]): Unit = {
        val searchResult = makeSearchResult(selected)
        val indexEntry = makeIndexEntry(searchResult)

        assertResult(searchResult)(UniversePackageCache.searchResult(indexEntry, images = None))
      }
    }

    // TODO: Test to confirm UniversePackageCache.search() is correct, once PR #283 is merged

  }

  "selected field is preserved by encoders/decoders" - {
    "SearchResult" - {
      "selected=true" in {
        assertEncodeDecode(makeSearchResult(selected = Some(true)))
      }

      "selected=false" in {
        assertEncodeDecode(makeSearchResult(selected = Some(false)))
      }

      "selected=None" - {
        "keep null" in {
          assertEncodeDecode(makeSearchResult(selected = None))
        }

        "drop null" in {
          assertEncodeDecode(makeSearchResult(selected = None), dropNullKeys = true)
        }
      }
    }

    "UniverseIndexEntry" - {
      "selected=true" in {
        assertEncodeDecode(makeIndexEntry(makeSearchResult(selected = Some(true))))
      }

      "selected=false" in {
        assertEncodeDecode(makeIndexEntry(makeSearchResult(selected = Some(false))))
      }

      "selected=None" - {
        "keep null" in {
          assertEncodeDecode(makeIndexEntry(makeSearchResult(selected = None)))
        }

        "drop null" in {
          assertEncodeDecode(makeIndexEntry(makeSearchResult(selected = None)), dropNullKeys = true)
        }
      }
    }

    "PackageDetails" - {
      "selected=true" in {
        assertEncodeDecode(makePackageDetails(makeSearchResult(selected = Some(true))))
      }

      "selected=false" in {
        assertEncodeDecode(makePackageDetails(makeSearchResult(selected = Some(false))))
      }

      "selected=None" - {
        "keep null" in {
          assertEncodeDecode(makePackageDetails(makeSearchResult(selected = None)))
        }

        "drop null" in {
          assertEncodeDecode(makePackageDetails(makeSearchResult(selected = None)), dropNullKeys = true)
        }
      }
    }

    def assertEncodeDecode[A](
      data: A,
      dropNullKeys: Boolean = false
    )(implicit d: Decoder[A], e: Encoder[A]): Unit = {
      assertResult(Xor.Right(data)) {
        decode[A](Printer.noSpaces.copy(dropNullKeys = dropNullKeys).pretty(data.asJson))
      }
    }
  }

}

object SelectedPackageSpec {

  def makeSearchResult(selected: Option[Boolean], name: String = "some-package"): SearchResult = {
    SearchResult(
      name = name,
      currentVersion = PackageDetailsVersion("1.2.3"),
      versions = Map(PackageDetailsVersion("1.2.3") -> ReleaseVersion("0")),
      description = "An arbitrary package",
      tags = Nil,
      selected = selected
    )
  }

  def makeIndexEntry(searchResult: SearchResult): UniverseIndexEntry = {
    UniverseIndexEntry(
      name = searchResult.name,
      currentVersion = searchResult.currentVersion,
      versions = searchResult.versions,
      description = searchResult.description,
      framework = searchResult.framework,
      tags = searchResult.tags,
      selected = searchResult.selected
    )
  }

  def makePackageDetails(searchResult: SearchResult): PackageDetails = {
    PackageDetails(
      packagingVersion = PackagingVersion("2.0"),
      name = searchResult.name,
      version = searchResult.currentVersion,
      maintainer = "mesosphere",
      description = searchResult.description,
      tags = searchResult.tags,
      selected = searchResult.selected
    )
  }

}
