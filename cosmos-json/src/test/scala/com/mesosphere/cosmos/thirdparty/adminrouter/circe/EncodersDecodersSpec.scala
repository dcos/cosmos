package com.mesosphere.cosmos.thirdparty.adminrouter.circe

import cats.data.Xor
import com.mesosphere.cosmos.thirdparty.adminrouter.model.DcosVersion
import com.mesosphere.cosmos.thirdparty.adminrouter.circe.Decoders._
import com.mesosphere.universe.v3.model.DcosReleaseVersion
import com.mesosphere.universe.v3.model.DcosReleaseVersion._
import io.circe.Json
import io.circe.syntax._
import org.scalatest.FreeSpec

class EncodersDecodersSpec extends FreeSpec {

  "DcosVersion" - {
    "decode" in {
      val json = Json.obj(
        "version" -> "1.7.1".asJson,
        "dcos-image-commit" -> "0defde84e7a71ebeb5dfeca0936c75671963df48".asJson,
        "bootstrap-id" -> "f12bff891be7108962c7c98e530e1f2cd8d4e56b".asJson
      )

      val (major, minor, patch) = (1, 7, 1)
      val expected = DcosVersion(
        DcosReleaseVersion(Version(major), List(Version(minor), Version(patch))),
        "0defde84e7a71ebeb5dfeca0936c75671963df48",
        "f12bff891be7108962c7c98e530e1f2cd8d4e56b"
      )

      val Xor.Right(actual) = json.as[DcosVersion]
      assertResult(expected)(actual)
    }
  }

}
