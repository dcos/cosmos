package com.mesosphere.universe.v3.model

import com.mesosphere.Generators._
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks
import scala.util.Random

final class SemVerSpec extends FreeSpec with PropertyChecks with Matchers {
  "For all SemVer => String => SemVer" in {

    forAll (genSemVer) { expected =>
        val string = expected.toString
        val actual = SemVer(string).get

        actual shouldBe expected
    }
  }

  "Test semver ordering" in {
    val expected = List(
      "1.0.0-alpha",
      "1.0.0-alpha.1",
      "1.0.0-alpha.beta",
      "1.0.0-beta",
      "1.0.0-beta.2",
      "1.0.0-beta.11",
      "1.0.0-rc.1",
      "1.0.0",
      "1.0.2",
      "1.2.0",
      "1.11.0",
      "1.11.11",
      "2",
      "11.11.11"
    ).map(SemVer(_).get)

    val actual = Random.shuffle(expected).sorted

    actual shouldBe expected
  }
}
