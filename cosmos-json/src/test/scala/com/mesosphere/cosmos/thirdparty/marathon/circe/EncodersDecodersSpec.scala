package com.mesosphere.cosmos.thirdparty.marathon.circe

import cats.data.Xor
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.circe.Decoders._
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import io.circe.Json
import io.circe.parse._
import io.circe.syntax._
import org.scalatest.FreeSpec

class EncodersDecodersSpec extends FreeSpec {

  "AppId" - {
    val relative: String = "cassandra/dcos"
    val absolute: String = s"/$relative"
    "encode" in {
      assertResult(Json.string(absolute))(AppId(relative).asJson)
    }
    "decode" in {
      val id = AppId(absolute)
      val Xor.Right(decoded) = decode[AppId](relative.asJson.noSpaces)
      assertResult(id)(decoded)
    }
  }

}
