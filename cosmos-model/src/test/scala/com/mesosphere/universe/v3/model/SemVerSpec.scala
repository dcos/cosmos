package com.mesosphere.universe.v3.model

import com.mesosphere.Generators
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
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
  val semVerGen: Gen[SemVer] = {
    val maxNumber = 999999999L
    val maxStringLength = 10
    val numbersGen = Gen.chooseNum(0, maxNumber)
    val preReleasesGen = for {
      seqSize <- Gen.chooseNum(0, 3)
      preReleases <- Gen.containerOfN[Seq, Either[String, Long]](
        seqSize,
        Gen.oneOf(
          numbersGen.map(Right(_)),
          Generators.maxSizedString(
            maxStringLength,
            Gen.alphaLowerChar
          ).filter(_.nonEmpty).map(Left(_))
        )
      )
    } yield preReleases

    val buildGen = Generators.maxSizedString(maxStringLength, Gen.alphaLowerChar).map {
      string => if (string.isEmpty) None else Some(string)
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
