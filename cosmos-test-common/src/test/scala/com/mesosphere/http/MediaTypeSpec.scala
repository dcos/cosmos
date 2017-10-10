package com.mesosphere.http

import com.mesosphere.Generators.Implicits._
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks
import scala.util.Success

final class MediaTypeSpec extends FreeSpec with PropertyChecks {

  private val testMediaType = MediaType(
    "application",
    MediaTypeSubType("vnd.dcos.test", Some("json")),
    Map("charset" -> "utf-8", "version" -> "v1")
  )

  "MediaType.parse(string) should" -  {
    "parse basic type" in  {
      val Success(t) = MediaType.parse("text/html")
      assertResult("text/html")(t.show)
    }

    "parse with suffix" in  {
      val Success(t) = MediaType.parse("""image/svg+xml""")
      assertResult("image")(t.`type`)
      assertResult("svg")(t.subType.value)
      assertResult(Some("xml"))(t.subType.suffix)
      assertResult("""image/svg+xml""")(t.show)
    }

    "parse parameters" in  {
      val Success(t) = MediaType.parse("""text/html; charset=utf-8; foo=bar""")
      assertResult("text")(t.`type`)
      assertResult("html")(t.subType.value)
      assertResult(Map(
        "charset" -> "utf-8",
        "foo" -> "bar"
      ))(t.parameters)
    }

    "lower-case type" in  {
      val Success(t) = MediaType.parse("""IMAGE/SVG+XML""")
      assertResult("""image/svg+xml""")(t.show)
    }

    "lower-case parameter names" in  {
      val Success(t) = MediaType.parse("""text/html; Charset=utf-8""")
      assertResult("text")(t.`type`)
      assertResult("html")(t.subType.value)
      assertResult(Map(
        "charset" -> "utf-8"
      ))(t.parameters)
    }

    "parse a vendor type" in {
      val Success(t) = MediaType.parse("application/vnd.dcos.test+json; charset=utf-8; version=v1")
      assertResult(testMediaType)(t)
    }

    "unquote parameter values" in  {
      val Success(t) = MediaType.parse("""text/html; charset="utf-8"""")
      assertResult("text")(t.`type`)
      assertResult("html")(t.subType.value)
      assertResult(Map(
        "charset" -> "utf-8"
      ))(t.parameters)
    }
  }

  "A MediaType should show correctly" - {
    "text/html" in {
      assertResult("text/html")(MediaType("text", MediaTypeSubType("html")).show)
    }
    "application/xhtml+xml" in {
      assertResult("application/xhtml+xml")(MediaType("application", MediaTypeSubType("xhtml", Some("xml"))).show)
    }
    "application/vnd.dcos.custom-request+json" in {
      assertResult("application/vnd.dcos.custom-request+json")(
        MediaType("application", MediaTypeSubType("vnd.dcos.custom-request", Some("json"))).show
      )
    }
  }

  "MediaType.show followed by MediaType.parse should be the identity function" in {
    forAll { (mediaType: MediaType) =>
      assertResult(Success(mediaType))(MediaType.parse(mediaType.show))
    }
  }

  // TODO package-add: Test for case-insensitive comparison

}
