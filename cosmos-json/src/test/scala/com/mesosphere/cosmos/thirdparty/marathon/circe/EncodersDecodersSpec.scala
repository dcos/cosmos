package com.mesosphere.cosmos.thirdparty.marathon.circe

import com.mesosphere.cosmos.thirdparty.marathon.circe.Decoders._
import com.mesosphere.cosmos.thirdparty.marathon.circe.Encoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import io.circe.Json
import io.circe.jawn.decode
import io.circe.syntax._
import org.scalatest.FreeSpec
import scala.util.Right

class EncodersDecodersSpec extends FreeSpec {

  "AppId" - {
    val relative: String = "cassandra/dcos"
    val absolute: String = s"/$relative"
    "encode" in {
      assertResult(Json.fromString(absolute))(AppId(relative).asJson)
    }
    "decode" in {
      val id = AppId(absolute)
      val Right(decoded) = decode[AppId](relative.asJson.noSpaces)
      assertResult(id)(decoded)
    }
  }

}
