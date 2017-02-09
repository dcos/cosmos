package com.mesosphere.util

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks
import scala.language.implicitConversions

final class PathSpec extends FreeSpec with PropertyChecks {

  import PathSpec._

  "A RelativePath can be extended by a RelativePath" in {
    forAll (genRelativePath, genRelativePath) { (basePath, extension) =>
      val fullPath = basePath / extension
      assertResult(fullPath.toList)(basePath.toList ++ extension.toList)
    }
  }

  "A RelativePath is equivalent to its toList representation" - {

    "List[String] => RelativePath => List[String]" in {
      forAll { (path: List[String]) =>
        assertResult(path)(RelativePath(path).toList)
      }
    }

    "RelativePath => List[String] => RelativePath" in {
      forAll (genRelativePath) { path =>
        assertResult(path)(RelativePath(path.toList))
      }
    }

  }

  "RelativePath.apply(String)" - {

    "fails on empty" in {
      assertResult(Left(RelativePath.Empty))(RelativePath(""))
    }

    "fails on absolute" in {
      forAll { (pathSuffix: String) =>
        assertResult(Left(RelativePath.Absolute))(RelativePath(s"/$pathSuffix"))
      }
    }

    "succeeds in all other cases" in {
      forAll { (path: String) =>
        whenever (path.nonEmpty && !path.startsWith("/")) {
          assert(RelativePath(path).isRight)
        }
      }
    }

  }

  "RelativePath has a normalized toString representation" in {
    forAll (genRelativePath) { path =>
      assertResult(Right(path))(RelativePath(path.toString))
    }
  }

}

object PathSpec {

  val genRelativePath: Gen[RelativePath] = {
    arbitrary[List[String]]
      .map(elements => RelativePath(elements.mkString(Path.Separator)))
      .flatMap(_.fold(_ => Gen.fail, Gen.const))
  }

  // TODO cruhland
  val genAbsolutePath: Gen[AbsolutePath] = Gen.const(AbsolutePath())

  val genPath: Gen[Path] = Gen.oneOf(genRelativePath, genAbsolutePath)

}
