package com.mesosphere.universe.common

import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.scalatest.FreeSpec
import scala.util.Right

class JsonUtilSpec extends FreeSpec {

  case class Foo(bar: Option[Int],far: Option[Int])

  implicit val encodeFoo: Encoder[Foo] = {
    deriveEncoder[Foo]
  }

  "dropNullKeys" in {
    val ls = Foo(None,None)
    val string = JsonUtil.dropNullKeysPrinter.pretty(ls.asJson)
    assertResult("{}")(string)
  }

}
