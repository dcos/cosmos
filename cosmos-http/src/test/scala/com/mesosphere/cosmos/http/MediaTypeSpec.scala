package com.mesosphere.cosmos.http

import com.twitter.util.Return
import org.scalatest.FreeSpec

class MediaTypeSpec extends FreeSpec {

  private val testMediaType = MediaType(
    "application",
    MediaTypeSubType("vnd.dcos.test", Some("json")),
    Map("charset" -> "utf-8", "version" -> "v1")
  )

  "MediaType.parse(string) should" -  {
    "parse basic type" in  {
      val Return(t) = MediaType.parse("text/html")
      assertResult("text/html")(t.show)
    }

    "parse with suffix" in  {
      val Return(t) = MediaType.parse("""image/svg+xml""")
      assertResult("image")(t.`type`)
      assertResult("svg")(t.subType.value)
      assertResult(Some("xml"))(t.subType.suffix)
      assertResult("""image/svg+xml""")(t.show)
    }

    "parse parameters" in  {
      val Return(t) = MediaType.parse("""text/html; charset=utf-8; foo=bar""")
      assertResult("text")(t.`type`)
      assertResult("html")(t.subType.value)
      assertResult(Map(
        "charset" -> "utf-8",
        "foo" -> "bar"
      ))(t.parameters)
    }

    "lower-case type" in  {
      val Return(t) = MediaType.parse("""IMAGE/SVG+XML""")
      assertResult("""image/svg+xml""")(t.show)
    }

    "lower-case parameter names" in  {
      val Return(t) = MediaType.parse("""text/html; Charset=utf-8""")
      assertResult("text")(t.`type`)
      assertResult("html")(t.subType.value)
      assertResult(Map(
        "charset" -> "utf-8"
      ))(t.parameters)
    }

    "parse a vendor type" in {
      val Return(t) = MediaType.parse("application/vnd.dcos.test+json; charset=utf-8; version=v1")
      assertResult(testMediaType)(t)
    }

    "unquote parameter values" in  {
      val Return(t) = MediaType.parse("""text/html; charset="utf-8"""")
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

}
