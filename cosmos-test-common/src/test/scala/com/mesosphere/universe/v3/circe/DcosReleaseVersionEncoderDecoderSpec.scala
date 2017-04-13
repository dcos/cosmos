package com.mesosphere.universe.v3.circe

import com.mesosphere.universe.v3.model.DcosReleaseVersion
import com.mesosphere.universe.v3.model.DcosReleaseVersion._
import io.circe.Json
import io.circe.jawn.decode
import io.circe.syntax._
import org.scalatest.FreeSpec
import scala.util.Right

class DcosReleaseVersionEncoderDecoderSpec extends FreeSpec {

  "DcosReleaseVersion" - {
    val str = "2.10.153-beta"
    val (major, minor, patch) = (2, 10, 153)
    val subVersions = List(Version(minor), Version(patch))
    val obj = DcosReleaseVersion(Version(major), subVersions, Some(Suffix("beta")))
    "decode"  in {
      val stringToDecode = s""""$str""""
      val Right(decoded) = decode[DcosReleaseVersion](stringToDecode)
      assertResult(obj)(decoded)
    }
    "encode" in {
      assertResult(Json.fromString(str))(obj.asJson)
    }
  }

}
