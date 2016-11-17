package com.mesosphere.universe.v3.model

import com.mesosphere.Generators
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
        forAll (Gen.negNum[Long]) { n =>
          whenever (n < 0) {
            assert(PackageDefinition.ReleaseVersion(n).isThrow)
          }
        }
      }

    }

    "ReleaseVersion.value" in {
      forAll (nonNegNum) { n =>
        assertResult(n)(PackageDefinition.ReleaseVersion(n).get.value)
      }
    }

    "ReleaseVersion$.ordering orders by value" in {
      forAll (nonNegNum, nonNegNum) { (a, b) =>
        whenever (a >= 0 && b >= 0) {
          val aVersion = PackageDefinition.ReleaseVersion(a).get
          val bVersion = PackageDefinition.ReleaseVersion(b).get

          assertResult(Ordering[Long].compare(a, b)) {
            Ordering[PackageDefinition.ReleaseVersion].compare(aVersion, bVersion)
          }
        }
      }
    }

  }

}

object PackageDefinitionSpec {
  val nonNegNum: Gen[Long] = Gen.sized(max => implicitly[Choose[Long]].choose(0, max.toLong))

  val packageNameGen: Gen[String] = {
    val maxPackageNameLength = 64
    Generators.maxSizedString(maxPackageNameLength, Gen.oneOf(Gen.numChar, Gen.alphaLowerChar))
  }

  val releaseVersionGen: Gen[PackageDefinition.ReleaseVersion] = for {
    num <- Gen.posNum[Long]
  } yield PackageDefinition.ReleaseVersion(num).get

  val versionGen: Gen[PackageDefinition.Version] = for {
    semver <- SemVerSpec.semVerGen
  } yield PackageDefinition.Version(semver.toString)

  val v3PackageGen: Gen[V3Package] = for {
    name <- packageNameGen
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

  val v2PackageGen: Gen[V2Package] = for {
    v3package <- v3PackageGen
  } yield V2Package(
    name=v3package.name,
    version=v3package.version,
    releaseVersion=v3package.releaseVersion,
    maintainer=v3package.maintainer,
    description=v3package.description,
    marathon=Marathon(ByteBuffer.allocate(0))
  )

  val packageDefinitionGen: Gen[PackageDefinition] = Gen.oneOf(v3PackageGen, v2PackageGen)
}
