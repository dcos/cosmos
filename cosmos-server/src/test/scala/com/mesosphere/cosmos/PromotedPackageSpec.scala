package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.handler.PackageSearchHandler
import com.mesosphere.cosmos.model.{SearchRequest, SearchResponse, SearchResult}
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.universe._
import com.twitter.util.{Await, Future}
import io.circe.parse._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Printer}
import io.finch.circe._
import org.mockito.Mockito._

final class PromotedPackageSpec extends UnitSpec {

  import PromotedPackageSpec._

  "Promoted packages appear first in search results" - {
    "with same package name" in {
      val promotedResult = makeSearchResult(promoted = Some(true))
      val ordinaryResult = makeSearchResult(promoted = Some(false))
      val anotherOrdinaryResult = makeSearchResult(promoted = None)

      val beforeSorting = List(ordinaryResult, promotedResult, anotherOrdinaryResult)
      val afterSorting = List(promotedResult, ordinaryResult, anotherOrdinaryResult)
      assertSearchResults(beforeSorting, afterSorting)
    }

    "with sorted package names" in {
      val first = makeSearchResult(promoted = Some(true), name = "antelope")
      val second = makeSearchResult(promoted = Some(true), name = "xylocarp")
      val third = makeSearchResult(promoted = Some(false), name = "aardvark")
      val fourth = makeSearchResult(promoted = Some(false), name = "zebra")

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

  "The promoted fields from the index are carried through to search results" - {
    "UniversePackageCache.searchResult" - {
      "promoted=true" in {
        assertPromotedPropagated(Some(true))
      }

      "promoted=false" in {
        assertPromotedPropagated(Some(false))
      }

      "promoted=None" in {
        assertPromotedPropagated(None)
      }

      def assertPromotedPropagated(promoted: Option[Boolean]): Unit = {
        val searchResult = makeSearchResult(promoted)
        val indexEntry = makeIndexEntry(searchResult)

        assertResult(searchResult)(UniversePackageCache.searchResult(indexEntry, images = None))
      }
    }

    // TODO: Test to confirm UniversePackageCache.search() is correct, once PR #283 is merged

  }

  "Promoted field is preserved by encoders/decoders" - {
    "SearchResult" - {
      "promoted=true" in {
        assertEncodeDecode(makeSearchResult(promoted = Some(true)))
      }

      "promoted=false" in {
        assertEncodeDecode(makeSearchResult(promoted = Some(false)))
      }

      "promoted=None" - {
        "keep null" in {
          assertEncodeDecode(makeSearchResult(promoted = None))
        }

        "drop null" in {
          assertEncodeDecode(makeSearchResult(promoted = None), dropNullKeys = true)
        }
      }
    }

    "UniverseIndexEntry" - {
      "promoted=true" in {
        assertEncodeDecode(makeIndexEntry(makeSearchResult(promoted = Some(true))))
      }

      "promoted=false" in {
        assertEncodeDecode(makeIndexEntry(makeSearchResult(promoted = Some(false))))
      }

      "promoted=None" - {
        "keep null" in {
          assertEncodeDecode(makeIndexEntry(makeSearchResult(promoted = None)))
        }

        "drop null" in {
          assertEncodeDecode(makeIndexEntry(makeSearchResult(promoted = None)), dropNullKeys = true)
        }
      }
    }

    "PackageDetails" - {
      "promoted=true" in {
        assertEncodeDecode(makePackageDetails(makeSearchResult(promoted = Some(true))))
      }

      "promoted=false" in {
        assertEncodeDecode(makePackageDetails(makeSearchResult(promoted = Some(false))))
      }

      "promoted=None" - {
        "keep null" in {
          assertEncodeDecode(makePackageDetails(makeSearchResult(promoted = None)))
        }

        "drop null" in {
          assertEncodeDecode(makePackageDetails(makeSearchResult(promoted = None)), dropNullKeys = true)
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

object PromotedPackageSpec {

  def makeSearchResult(promoted: Option[Boolean], name: String = "some-package"): SearchResult = {
    SearchResult(
      name = name,
      currentVersion = PackageDetailsVersion("1.2.3"),
      versions = Map(PackageDetailsVersion("1.2.3") -> ReleaseVersion("0")),
      description = "An arbitrary package",
      tags = Nil,
      promoted = promoted
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
      promoted = searchResult.promoted
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
      promoted = searchResult.promoted
    )
  }

}
