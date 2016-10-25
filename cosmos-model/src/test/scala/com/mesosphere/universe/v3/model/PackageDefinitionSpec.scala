package com.mesosphere.universe.v3.model

import java.nio.ByteBuffer
import org.scalacheck.Gen
import org.scalacheck.Gen.Choose
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks

final class PackageDefinitionSpec extends FreeSpec with PropertyChecks {

  import PackageDefinitionSpec._

  "PackageDefinition$.ReleaseVersion" - {

    "ReleaseVersion$.apply should" - {

      "succeed on non-negative numbers" in {
        forAll (nonNegNum) { n =>
          whenever (n >= 0) {
            assert(PackageDefinition.ReleaseVersion(n).isReturn)
          }
        }
      }

      "fail on negative numbers" in {
        forAll (Gen.negNum[Int]) { n =>
          whenever (n < 0) {
            assert(PackageDefinition.ReleaseVersion(n).isThrow)
          }
        }
      }

    }

    "ReleaseVersion.value" in {
      assertResult(42)(PackageDefinition.ReleaseVersion(42).get.value)
    }

    "ReleaseVersion$.ordering orders by value" in {
      forAll (nonNegNum, nonNegNum) { (a, b) =>
        whenever (a >= 0 && b >= 0) {
          val aVersion = PackageDefinition.ReleaseVersion(a).get
          val bVersion = PackageDefinition.ReleaseVersion(b).get

          assertResult(Ordering[Int].compare(a, b)) {
            Ordering[PackageDefinition.ReleaseVersion].compare(aVersion, bVersion)
          }
        }
      }
    }

  }

}

object PackageDefinitionSpec {
  val nonNegNum: Gen[Int] = Gen.sized(max => implicitly[Choose[Int]].choose(0, max))

  implicit val releaseVersionGen: Gen[PackageDefinition.ReleaseVersion] = for {
    num <- Gen.posNum[Int]
  } yield PackageDefinition.ReleaseVersion(num).get

  implicit val versionGen: Gen[PackageDefinition.Version] = for {
    semver <- SemVerSpec.semVerGen
  } yield PackageDefinition.Version(semver.toString)

  implicit val v3PackageGen: Gen[V3Package] = for {
    name <- Gen.alphaStr
    version <- versionGen
    releaseVersion <- releaseVersionGen
    maintainer <- Gen.alphaStr
    description <- Gen.alphaStr
  } yield V3Package(
    name=name,
    version=version,
    releaseVersion=releaseVersion,
    maintainer=maintainer,
    description=description
  )

  implicit val v2PackageGen: Gen[V2Package] = for {
    v3package <- v3PackageGen
  } yield V2Package(
    name=v3package.name,
    version=v3package.version,
    releaseVersion=v3package.releaseVersion,
    maintainer=v3package.maintainer,
    description=v3package.description,
    marathon=Marathon(ByteBuffer.allocate(0))
  )

  implicit val packageDefinitionGen: Gen[PackageDefinition] =
    Gen.oneOf(v3PackageGen, v2PackageGen)
}
