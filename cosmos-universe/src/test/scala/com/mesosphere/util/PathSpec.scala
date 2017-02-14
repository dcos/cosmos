package com.mesosphere.util

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks
import scala.language.implicitConversions
import scala.reflect.ClassTag

final class PathSpec extends FreeSpec with PropertyChecks {

  import PathSpec._

  "Constructing Paths" - {

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
          whenever(path.nonEmpty && !path.startsWith("/")) {
            assert(RelativePath(path).isRight)
          }
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

      "fails on more than one leading separator" in {
        forAll (arbitrary[String], Gen.size) { (path, leadingSeparators) =>
          whenever (leadingSeparators > 1) {
            assertResult(Left(AbsolutePath.BadRoot)) {
              AbsolutePath(Path.Separator * leadingSeparators + path)
            }
          }
        }
      }

      "succeeds in all other cases" in {
        forAll { (path: String) =>
          assert(AbsolutePath(s"/$path").isRight)
        }
      }

    }

  }

  "String representations of Paths" - {

    "Path => String => Path preserves the value" - {

      "RelativePath" in {
        forAll (genRelativePath) { path =>
          assertResult(Right(path))(RelativePath(path.toString))
        }
      }

      "AbsolutePath" in {
        forAll (genAbsolutePath) { path =>
          assertResult(Right(path))(AbsolutePath(path.toString))
        }
      }

    }

    "String => Path => String normalizes the value" - {

      "RelativePath" in {
        assertNormalization(formatPath = identity, buildPath = RelativePath(_))
      }

      "AbsolutePath" in {
        assertNormalization(formatPath = Path.Separator + _, buildPath = AbsolutePath(_))
      }

      def assertNormalization(
        formatPath: String => String,
        buildPath: String => Either[_, Path]
      ): Unit = {
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

            val pathStr = formatPath(elementsWithExtraSeparators.mkString)
            val normalizedPathStr = formatPath(elements.mkString(Path.Separator))

            assertResult(Right(normalizedPathStr))(buildPath(pathStr).right.map(_.toString))
          }
        }
      }

    }

  }

  "Extending Paths" - {

    "A RelativePath can be extended by a RelativePath" in {
      assertPathExtension(genRelativePath)
    }

    "An AbsolutePath can be extended by a RelativePath" in {
      assertPathExtension(genAbsolutePath)
    }

    def assertPathExtension[P <: Path](genBasePath: Gen[P]): Unit = {
      forAll (genBasePath, genRelativePath) { (basePath, extension) =>
        val basePathStr = basePath.toString
        val extensionStr = extension.toString
        val fullPathStr = (basePath / extension).toString

        assert(fullPathStr.startsWith(basePathStr))
        assert(fullPathStr.endsWith(extensionStr))
        assertResult(Path.Separator)(fullPathStr.stripPrefix(basePathStr).stripSuffix(extensionStr))
      }
    }

  }

  "Retrieving path elements" - {

    behave like elementsTestCases(buildRelativePath, genRelativePath)

    behave like elementsTestCases(buildAbsolutePath, genAbsolutePath)

    def elementsTestCases[P <: Path : ClassTag](
      buildPath: List[String] => Either[_, P],
      genPath: Gen[P]
    ): Unit = {
      val pathType = implicitly[ClassTag[P]].runtimeClass.getSimpleName

      s"$pathType => elements => $pathType is identity" in {
        forAll (genPath) { path =>
          assertResult(path)(buildPath(path.elements).right.get)
        }
      }

      s"elements => $pathType => elements is identity" in {
        forAll (genPathElements) { elements =>
          assertResult(elements)(buildPath(elements).right.get.elements)
        }
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
    genPathElements.map(elements => buildRelativePath(elements).right.get)
  }

  val genAbsolutePath: Gen[AbsolutePath] = {
    genPathElements.map(elements => buildAbsolutePath(elements).right.get)
  }

  val genPath: Gen[Path] = Gen.oneOf(genRelativePath, genAbsolutePath)

  def validPathElements(elements: List[String]): Boolean = {
    elements.nonEmpty && elements.forall(e => e.nonEmpty && !e.contains(Path.Separator))
  }

  def buildRelativePath(elements: List[String]): Either[RelativePath.Error, RelativePath] = {
    RelativePath(elements.mkString(Path.Separator))
  }

  def buildAbsolutePath(elements: List[String]): Either[AbsolutePath.Error, AbsolutePath] = {
    AbsolutePath(elements.mkString(Path.Separator, Path.Separator, ""))
  }

}
