package com.mesosphere.cosmos

import _root_.io.circe.jawn.decode
import _root_.io.circe.syntax._
import _root_.io.circe.Decoder
import _root_.io.circe.Encoder
import _root_.io.circe.Printer
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.SearchResult
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v2.circe.Decoders._
import com.mesosphere.universe.v2.circe.Encoders._
import com.mesosphere.universe.v2.model._
import com.twitter.bijection.Conversion.asMethod
import org.mockito.Mockito._
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import org.scalatest.mockito.MockitoSugar
import scala.util.Right

final class SelectedPackageSpec extends FreeSpec with MockitoSugar {

  import SelectedPackageSpec._

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
    )(implicit d: Decoder[A], e: Encoder[A]): Assertion = {
      assertResult(Right(data)) {
        decode[A](Printer.noSpaces.copy(dropNullKeys = dropNullKeys).pretty(data.asJson))
      }
    }
  }

}

object SelectedPackageSpec {

  def makeSearchResult(selected: Option[Boolean], name: String = "some-package"): SearchResult = {
    SearchResult(
      name = name,
      currentVersion = universe.v3.model.Version("1.2.3"),
      versions = Map(
        universe.v3.model.Version("1.2.3") ->
          universe.v3.model.ReleaseVersion(0)
      ),
      description = "An arbitrary package",
      tags = Nil,
      framework = false,
      selected = selected
    )
  }

  def makePackageDetails(searchResult: SearchResult): PackageDetails = {
    PackageDetails(
      packagingVersion = PackagingVersion("2.0"),
      name = searchResult.name,
      version = searchResult.currentVersion.as[universe.v2.model.PackageDetailsVersion],
      maintainer = "mesosphere",
      description = searchResult.description,
      tags = searchResult.tags.as[List[String]],
      selected = searchResult.selected
    )
  }

}
