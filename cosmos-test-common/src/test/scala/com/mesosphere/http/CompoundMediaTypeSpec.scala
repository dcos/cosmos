package com.mesosphere.http

import com.mesosphere.universe.MediaTypes
import org.scalatest.FreeSpec
import scala.util.Success

class CompoundMediaTypeSpec extends FreeSpec {

  private val applicationJson = MediaType("application", MediaTypeSubType("json"), Map("charset" -> "utf-8"))
  private val v1 = withV(applicationJson, "v1")
  private val v2 = withV(applicationJson, "v2")

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

      val Success(actual) = CompoundMediaTypeParser.parse("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")

      assertResult(expected)(actual.mediaTypes)
    }

    "v1 and v2 install-response" in {
      val expected = Set(v1, v2)

      val Success(actual) = CompoundMediaTypeParser.parse("application/json;charset=utf-8;version=v2, application/json;charset=utf-8;version=v1")

      assertResult(expected)(actual.mediaTypes)
    }

    "single value" in {
      val expected = Set(v1)

      val Success(actual) = CompoundMediaTypeParser.parse("application/json;charset=utf-8;version=v1")

      assertResult(expected)(actual.mediaTypes)
    }

    "no value" in {
      val Success(actual) = CompoundMediaTypeParser.parse("")
      assertResult(Set.empty)(actual.mediaTypes)
    }
  }


  "CompoundMediaType.calculateIntersectionAndOrder should be able to" - {
    "return a prioritized intersection" in {
      val expected = List(v2, v1)

      val cmt = CompoundMediaType(Set(
        withQ(v2, 0.9),
        withQ(v1, 0.8)
      ))

      val accepted = Set(v1, v2)

      val actual = CompoundMediaType.calculateIntersectionAndOrder(cmt, accepted)

      assertResult(expected)(actual)
    }

    "return an empty list when no intersection" in {
      val expected = List()

      val cmt = CompoundMediaType(Set(
        withQ(v2, 0.9),
        withQ(v1, 0.8)
      ))

      val accepted = Set(
        MediaTypes.universeV2Package
      )

      val actual = CompoundMediaType.calculateIntersectionAndOrder(cmt, accepted)

      assertResult(expected)(actual)
    }

    "exclude any MediaType with a qvalue of 0.0 to be omitted" in {
      val expected = List(
        v2
      )

      val cmt = CompoundMediaType(Set(
        withQ(v2, 0.9),
        withQ(v1, 0.0)
      ))

      val accepted = Set(
        v1,
        v2
      )

      val actual = CompoundMediaType.calculateIntersectionAndOrder(cmt, accepted)

      assertResult(expected)(actual)

    }
  }

  "CompoundMediaType.getMostAppropriateMediaType should be able to" - {
    "return the highest quality value when multiple choices" in {
      val expected = Some(
        v2
      )

      val cmt = CompoundMediaType(Set(
        withQ(v2, 0.9),
        withQ(v1, 0.8)
      ))

      val accepted = Set(
        v1,
        v2
      )

      val actual = cmt.getMostAppropriateMediaType(accepted)

      assertResult(expected)(actual)
    }

    "return an empty list when no intersection" in {
      val expected = None

      val cmt = CompoundMediaType(Set(
        withQ(v2, 0.9),
        withQ(v1, 0.8)
      ))

      val accepted = Set(
        MediaTypes.universeV2Package
      )

      val actual = cmt.getMostAppropriateMediaType(accepted)

      assertResult(expected)(actual)
    }
  }

  private[this] def withQ(mt: MediaType, q: Double): MediaType = {
    val newParams = mt.parameters + ("q" -> q.toString)
    mt.copy(parameters = newParams)
  }

  private[this] def withV(mt: MediaType, version: String): MediaType = {
    val newParams = mt.parameters + ("version" -> version)
    mt.copy(parameters = newParams)
  }

}
