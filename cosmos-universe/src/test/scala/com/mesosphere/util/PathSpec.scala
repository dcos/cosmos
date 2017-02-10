package com.mesosphere.util

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks
import scala.language.implicitConversions

final class PathSpec extends FreeSpec with PropertyChecks {

  import PathSpec._

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

  "RelativePath preserves its String representation" in {
    forAll (genRelativePath) { path =>
      assertResult(Right(path))(RelativePath(path.toString))
    }
  }

  "RelativePath has a normalized toString representation" in {
    val genElementsAndSeparatorCounts = for {
      elements <- genPathElements
      size <- Gen.size
      counts <- Gen.listOfN(elements.size, Gen.chooseNum(1, size))
    } yield (elements, counts)

    forAll (genElementsAndSeparatorCounts) { case (elements, counts) =>
      val elementsValid = validPathElements(elements)

      whenever (elementsValid && elements.size == counts.size && counts.forall(_ > 0)) {
        val elementsWithExtraSeparators = elements.zip(counts)
          .flatMap { case (element, count) => element :: List.fill(count)(Path.Separator) }

        val pathStr = elementsWithExtraSeparators.mkString
        val normalizedPathStr = elements.mkString(Path.Separator)

        assertResult(Right(normalizedPathStr))(RelativePath(pathStr).right.map(_.toString))
      }
    }
  }

  "AbsolutePath.apply(String)" - {

    "fails on empty" in {
      assertResult(Left(AbsolutePath.Empty))(AbsolutePath(""))
    }

    "fails on relative" in {
      forAll { (path: String) =>
        whenever (path.nonEmpty && !path.startsWith(Path.Separator)) {
          assertResult(Left(AbsolutePath.Relative))(AbsolutePath(path))
        }
      }
    }

    "succeeds in all other cases" in {
      forAll { (path: String) =>
        assert(AbsolutePath(s"/$path").isRight)
      }
    }

  }

  "A RelativePath can be extended by a RelativePath" in {
    forAll (genRelativePath, genRelativePath) { (basePath, extension) =>
      val fullPath = basePath / extension

      assertResult(Path.Separator) {
        fullPath.toString.stripPrefix(basePath.toString).stripSuffix(extension.toString)
      }
    }
  }

}

object PathSpec {

  val genPathElements: Gen[List[String]] = {
    val genElement = Gen.nonEmptyListOf(arbitrary[Char]).map(_.mkString)
    Gen.nonEmptyListOf(genElement).suchThat(validPathElements)
  }

  val genRelativePath: Gen[RelativePath] = {
    genPathElements.map(elements => RelativePath(elements.mkString(Path.Separator)).right.get)
  }

  // TODO cruhland
  val genAbsolutePath: Gen[AbsolutePath] = Gen.const(AbsolutePath())

  val genPath: Gen[Path] = Gen.oneOf(genRelativePath, genAbsolutePath)

  def validPathElements(elements: List[String]): Boolean = {
    elements.nonEmpty && elements.forall(e => e.nonEmpty && !e.contains(Path.Separator))
  }

}
