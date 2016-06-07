package com.mesosphere.universe.v3.circe

import cats.data.Xor
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.universe.v3.model.DcosReleaseVersion
import com.mesosphere.universe.v3.model.DcosReleaseVersion._
import io.circe.Json
import io.circe.parse._
import io.circe.syntax._
import org.scalatest.FreeSpec

class DcosReleaseVersionEncoderDecoderSpec extends FreeSpec {

  "DcosReleaseVersion" - {
    val str = "2.10.153-beta"
    val obj = DcosReleaseVersion(Version(2), List(Version(10), Version(153)), Some(Suffix("beta")))
    "decode"  in {
      assertResult(Xor.Right(obj))(decode[DcosReleaseVersion](str))
    }
    "encode" in {
      assertResult(Json.string(str))(obj.asJson)
    }
  }

}
