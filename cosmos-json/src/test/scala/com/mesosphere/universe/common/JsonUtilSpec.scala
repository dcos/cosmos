package com.mesosphere.universe.common

import org.scalatest.FreeSpec

import java.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util
import io.circe.syntax._
import cats.data.Xor.Right
import cats.data.Xor.Left
import cats.data.Xor
import io.circe.Printer
import io.circe.Encoder
import io.circe.generic.semiauto._

class JsonUtilSpec extends FreeSpec {
  case class Foo(val bar:Option[Int], val far:Option[Int])

  implicit val encodeFoo: Encoder[Foo] = {
    deriveFor[Foo].encoder
  }
  "dropNullKeys" in {
    val ls = Foo(None,None)
    val string = JsonUtil.dropNullKeys.pretty(ls.asJson)
    assertResult("{}")(string)
  }
  "decode64" in {
    val ls = List("hello", "world")
    val string = JsonUtil.dropNullKeys.pretty(ls.asJson)
    val string64 = Base64.getEncoder.encodeToString(string.getBytes(StandardCharsets.UTF_8))
    assertResult(Right(ls))(JsonUtil.decode64[List[String]](string64))
  }
}
