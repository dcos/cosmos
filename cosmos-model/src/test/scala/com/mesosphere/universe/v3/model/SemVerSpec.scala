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

    forAll (SemVerSpec.semVerGen) { expected =>
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

object SemVerSpec {
  private[this] def sizedString(maxSize: Int): Gen[String] = Gen.sized { size =>
    for {
      array <- Gen.containerOfN[Array, Char](
        size % maxSize,
        Gen.alphaLowerChar
      )
    } yield new String(array)
  }

  val semVerGen: Gen[SemVer] = Gen.sized { size =>
    val numbersGen = for (n <- Gen.choose(0, 999999999L)) yield n
    val preReleasesGen = Gen.containerOfN[Seq, Either[String, Long]](
      (size % 4),
      Gen.oneOf(
        numbersGen.map(Right(_)),
        sizedString((size % 10) + 1).filter(_.nonEmpty).map(Left(_))
      )
    )

    val buildGen = sizedString((size % 10) + 1).map { string =>
      if (string.isEmpty) None else Some(string)
    }

    for {
      major <- numbersGen
      minor <- numbersGen
      patch <- numbersGen
      preReleases <- preReleasesGen
      build <- buildGen
    } yield SemVer(major, minor, patch, preReleases, build)
  }
}
