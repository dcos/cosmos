package com.mesosphere.universe.common

import io.circe.Encoder
import io.circe.Json
import io.circe.JsonObject
import io.circe.generic.semiauto._
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.scalatest.FreeSpec
import org.scalatest.prop.TableDrivenPropertyChecks

class JsonUtilSpec extends FreeSpec with TableDrivenPropertyChecks {

  case class Foo(bar: Option[Int],far: Option[Int])

  implicit val encodeFoo: Encoder[Foo] = {
    deriveEncoder[Foo]
  }

  "dropNullKeys" in {
    val ls = Foo(None,None)
    val string = JsonUtil.dropNullKeysPrinter.pretty(ls.asJson)
    assertResult("{}")(string)
  }

  "Merging JSON objects" - {

    "should pass on all examples" in {
      forAll (Examples) { (defaultsJson, optionsJson, mergedJson) =>
        assertResult(mergedJson)(JsonUtil.merge(defaultsJson, optionsJson))
      }
    }
  }

  private[this] val Examples = Table(
    ("defaults JSON", "options JSON", "merged JSON"),
    (JsonObject.empty, JsonObject.empty, JsonObject.empty),

    (
      JsonObject.empty,
      JsonObject.singleton("a", Json.False),
      JsonObject.singleton("a", Json.False)
    ),
    (
      JsonObject.singleton("a", Json.False),
      JsonObject.empty,
      JsonObject.singleton("a", Json.False)
    ),
    (
      JsonObject.singleton("a", Json.False),
      JsonObject.singleton("a", Json.True),
      JsonObject.singleton("a", Json.True)
    ),
    (
      JsonObject.singleton("a", Json.obj("a" -> Json.False)),
      JsonObject.singleton("a", Json.obj()),
      JsonObject.singleton("a", Json.obj("a" -> Json.False))
    ),
    (
      JsonObject.singleton("a", Json.obj("a" -> Json.False)),
      JsonObject.singleton("a", Json.obj("a" -> Json.True)),
      JsonObject.singleton("a", Json.obj("a" -> Json.True))
    ),
    (
      JsonObject.singleton("a", Json.obj("a" -> Json.False)),
      JsonObject.singleton("a", Json.obj("b" -> Json.False)),
      JsonObject.singleton("a", Json.obj("a" -> Json.False, "b" -> Json.False))
    ),
    (
      JsonObject.singleton("a", Json.obj("a" -> Json.False)),
      JsonObject.singleton("a", Json.True),
      JsonObject.singleton("a", Json.True)
    )
  )

}
