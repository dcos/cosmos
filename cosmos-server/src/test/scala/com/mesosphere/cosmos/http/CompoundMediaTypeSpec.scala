package com.mesosphere.cosmos.http

import com.mesosphere.cosmos.http.CompoundMediaTypeOps.compoundMediaTypeToCompoundMediaTypeOps
import com.twitter.util.Return
import org.scalatest.FreeSpec

class CompoundMediaTypeSpec extends FreeSpec {


  "should be able to cleanly parse" - {
    // taken from google-chrome 52.0.2743.116 (64-bit)
    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8" in {
      val expected = Set(
        MediaTypeParser.parseUnsafe("text/html;q=0.9"),
        MediaTypeParser.parseUnsafe("application/xhtml+xml;q=0.9"),
        MediaTypeParser.parseUnsafe("application/xml;q=0.9"),
        MediaTypeParser.parseUnsafe("image/webp;q=0.8"),
        MediaTypeParser.parseUnsafe("*/*;q=0.8")
      )

      val Return(actual) = CompoundMediaTypeParser.parse("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")

      assertResult(expected)(actual.mediaTypes)
    }

    "v1 and v2 install-response" in {
      val expected = Set(
        MediaTypes.V1InstallResponse,
        MediaTypes.V2InstallResponse
      )

      val Return(actual) = CompoundMediaTypeParser.parse("application/vnd.dcos.package.install-response+json;charset=utf-8;version=v2, application/vnd.dcos.package.install-response+json;charset=utf-8;version=v1")

      assertResult(expected)(actual.mediaTypes)
    }

    "single value" in {
      val expected = Set(
        MediaTypes.V1InstallResponse
      )

      val Return(actual) = CompoundMediaTypeParser.parse("application/vnd.dcos.package.install-response+json;charset=utf-8;version=v1")

      assertResult(expected)(actual.mediaTypes)
    }

    "no value" in {
      val Return(actual) = CompoundMediaTypeParser.parse("")
      assertResult(Set.empty)(actual.mediaTypes)
    }
  }


  "CompoundMediaTypeOps.calculateIntersectionAndOrder should be able to" - {
    "return a prioritized intersection" in {
      val expected = List(
        MediaTypes.V2InstallResponse,
        MediaTypes.V1InstallResponse
      )

      val cmt = CompoundMediaType(Set(
        withQ(MediaTypes.V2InstallResponse, 0.9),
        withQ(MediaTypes.V1InstallResponse, 0.8)
      ))

      val accepted = Set(
        MediaTypes.V1InstallResponse,
        MediaTypes.V2InstallResponse
      )

      val actual = CompoundMediaTypeOps.calculateIntersectionAndOrder(cmt, accepted)

      assertResult(expected)(actual)
    }

    "return an empty list when no intersection" in {
      val expected = List()

      val cmt = CompoundMediaType(Set(
        withQ(MediaTypes.V2InstallResponse, 0.9),
        withQ(MediaTypes.V1InstallResponse, 0.8)
      ))

      val accepted = Set(
        MediaTypes.applicationZip
      )

      val actual = CompoundMediaTypeOps.calculateIntersectionAndOrder(cmt, accepted)

      assertResult(expected)(actual)
    }

    "exclude any MediaType with a qvalue of 0.0 to be omitted" in {
      val expected = List(
        MediaTypes.V2InstallResponse
      )

      val cmt = CompoundMediaType(Set(
        withQ(MediaTypes.V2InstallResponse, 0.9),
        withQ(MediaTypes.V1InstallResponse, 0.0)
      ))

      val accepted = Set(
        MediaTypes.V1InstallResponse,
        MediaTypes.V2InstallResponse
      )

      val actual = CompoundMediaTypeOps.calculateIntersectionAndOrder(cmt, accepted)

      assertResult(expected)(actual)

    }
  }

  "CompoundMediaTypeOps.getMostAppropriateMediaType should be able to" - {
    "return the highest quality value when multiple choices" in {
      val expected = Some(
        MediaTypes.V2InstallResponse
      )

      val cmt = CompoundMediaType(Set(
        withQ(MediaTypes.V2InstallResponse, 0.9),
        withQ(MediaTypes.V1InstallResponse, 0.8)
      ))

      val accepted = Set(
        MediaTypes.V1InstallResponse,
        MediaTypes.V2InstallResponse
      )

      val actual = cmt.getMostAppropriateMediaType(accepted)

      assertResult(expected)(actual)
    }

    "return an empty list when no intersection" in {
      val expected = None

      val cmt = CompoundMediaType(Set(
        withQ(MediaTypes.V2InstallResponse, 0.9),
        withQ(MediaTypes.V1InstallResponse, 0.8)
      ))

      val accepted = Set(
        MediaTypes.applicationZip
      )

      val actual = cmt.getMostAppropriateMediaType(accepted)

      assertResult(expected)(actual)
    }
  }

  private[this] def withQ(mt: MediaType, q: Double): MediaType = {
    val newParams = mt.parameters + ("q" -> q.toString)
    mt.copy(parameters = newParams)
  }

}
