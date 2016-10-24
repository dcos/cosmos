package com.mesosphere.universe.v3.model

import org.scalacheck.Gen
import org.scalacheck.Arbitrary
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks
import scala.util.Random
import scala.util.Success
import scala.util.Try

final class SemVerSpec extends FreeSpec with PropertyChecks with Matchers {
  "For all SemVer => String => SemVer" in {
    val numbers = for (n <- Gen.choose(0, Long.MaxValue)) yield n
    val preReleases = Gen.containerOf[Seq, Either[String, Long]](
      Gen.oneOf(
        numbers.map(Right(_)),
        Gen.alphaStr.filter(_.nonEmpty).map(Left(_))
      )
    )
    val build = Gen.alphaStr.map(string => if (string.isEmpty) None else Some(string))

    forAll (numbers, numbers, numbers, preReleases, build) {
      (major, minor, patch, preReleases, build) =>
        val expected = SemVer(major, minor, patch, preReleases, build)
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
