package com.mesosphere.util

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks
import scala.language.implicitConversions
import scala.reflect.ClassTag

final class PathSpec extends FreeSpec with PropertyChecks {

  import PathSpec._

  "Constructing Paths" - {

    "RelativePath" - {

      "apply(String)" - {
        behave like validationCases { path =>
          try { Right(RelativePath(path)) } catch { case e: RelativePath.Error => Left(e) }
        }
      }

      "validate(String)" - {
        behave like validationCases(RelativePath.validate)
      }

      def validationCases(buildPath: String => Either[RelativePath.Error, RelativePath]): Unit = {

        "succeeds on empty" in {
          assertResult(Right(RelativePath.Empty))(buildPath(""))
        }

        "fails on absolute" in {
          forAll { (pathSuffix: String) =>
            assertResult(Left(RelativePath.Absolute))(buildPath(s"/$pathSuffix"))
          }
        }

        "succeeds in all other cases" in {
          forAll { (path: String) =>
            whenever (path.nonEmpty && !path.startsWith("/")) {
              assert(buildPath(path).isRight)
            }
          }
        }

      }

    }

    "AbsolutePath" - {

      "apply(String)" - {
        behave like validationCases { path =>
          try { Right(AbsolutePath(path)) } catch { case e: AbsolutePath.Error => Left(e) }
        }
      }

      "validate(String)" - {
        behave like validationCases(AbsolutePath.validate)
      }

      def validationCases(buildPath: String => Either[AbsolutePath.Error, AbsolutePath]): Unit = {

        "fails on empty" in {
          assertResult(Left(AbsolutePath.Empty))(buildPath(""))
        }

        "fails on relative" in {
          forAll { (path: String) =>
            whenever (path.nonEmpty && !path.startsWith(Path.Separator)) {
              assertResult(Left(AbsolutePath.Relative))(buildPath(path))
            }
          }
        }

        "fails on more than one leading separator" in {
          forAll(arbitrary[String], Gen.size) { (path, leadingSeparators) =>
            whenever (leadingSeparators > 1) {
              assertResult(Left(AbsolutePath.BadRoot)) {
                buildPath(Path.Separator * leadingSeparators + path)
              }
            }
          }
        }

        "succeeds in all other cases" in {
          forAll { (path: String) =>
            assert(buildPath(s"/$path").isRight)
          }
        }

      }

    }

  }

  "String representations of Paths" - {

    "Path => String => Path preserves the value" - {

      "RelativePath" in {
        forAll (genRelativePath) { path =>
          assertResult(path)(RelativePath(path.toString))
        }
      }

      "AbsolutePath" in {
        forAll (genAbsolutePath) { path =>
          assertResult(path)(AbsolutePath(path.toString))
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
        buildPath: String => Path
      ): Assertion = {
        val genElementsAndSeparatorCounts = for {
          elements <- genPathElements
          size <- Gen.size
          counts <- Gen.listOfN(elements.size, Gen.chooseNum(1, math.max(1, size)))
        } yield (elements, counts)

        forAll (genElementsAndSeparatorCounts) { case (elements, counts) =>
          val elementsValid = validPathElements(elements)

          whenever (elementsValid && elements.size == counts.size && counts.forall(_ > 0)) {
            val elementsWithExtraSeparators = elements.zip(counts)
              .flatMap { case (element, count) => element :: List.fill(count)(Path.Separator) }

            val pathStr = formatPath(elementsWithExtraSeparators.mkString)
            val normalizedPathStr = formatPath(elements.mkString(Path.Separator))

            assertResult(normalizedPathStr)(buildPath(pathStr).toString)
          }
        }
      }

    }

  }

  "Extending Paths" - {

    "by a single element" - {

      "for RelativePaths" - {

        behave like singleElementCases(genRelativePath)

      }

      "for AbsolutePaths" - {

        behave like singleElementCases(genAbsolutePath)

      }

      def singleElementCases[P <: Path](genBasePath: Gen[P]): Unit = {

        "fails on an empty element" in {
          forAll (genBasePath) { basePath =>
            val _ = intercept[IllegalArgumentException](basePath / "")
          }
        }

        "fails on an element containing a separator" in {
          forAll (genBasePath, arbitrary[String], arbitrary[String]) {
            (basePath, prefix, suffix) =>
              val _ = intercept[IllegalArgumentException](basePath / s"$prefix/$suffix")
          }
        }

        "succeeds in all other cases, appending the element" in {
          forAll (genBasePath, arbitrary[String]) { (basePath, element) =>
            whenever (element.nonEmpty && !element.contains(Path.Separator)) {
              val fullElements = (basePath / element).elements
              assertResult(basePath.elements)(fullElements.init)
              assertResult(element)(fullElements.last)
            }
          }
        }

      }

    }

    "by a RelativePath" - {

      "A RelativePath can be extended by a RelativePath" in {
        assertPathExtension(genRelativePath)
      }

      "An AbsolutePath can be extended by a RelativePath" in {
        assertPathExtension(genAbsolutePath)
      }

      def assertPathExtension[P <: Path](genBasePath: Gen[P]): Assertion = {
        forAll (genBasePath, genRelativePath) { (basePath, extension) =>
          val basePathStr = basePath.toString
          val extensionStr = extension.toString
          val fullPathStr = basePath.resolve(extension).toString

          assert(fullPathStr.startsWith(basePathStr))
          assert(fullPathStr.endsWith(extensionStr))

          val expected =
            if (basePath.elements.isEmpty || extension.elements.isEmpty) "" else Path.Separator
          val actual = fullPathStr.stripPrefix(basePathStr).stripSuffix(extensionStr)
          assertResult(expected)(actual)
        }
      }

    }

  }

  "Retrieving path elements" - {

    behave like elementsTestCases(buildRelativePath, genRelativePath)

    behave like elementsTestCases(buildAbsolutePath, genAbsolutePath)

    def elementsTestCases[P <: Path: ClassTag](
      buildPath: Vector[String] => P,
      genPath: Gen[P]
    ): Unit = {
      val pathType = implicitly[ClassTag[P]].runtimeClass.getSimpleName

      s"$pathType => elements => $pathType is identity" in {
        forAll (genPath) { path =>
          assertResult(path)(buildPath(path.elements))
        }
      }

      s"elements => $pathType => elements is identity" in {
        forAll (genPathElements) { elements =>
          assertResult(elements)(buildPath(elements).elements)
        }
      }

    }

  }

  "Relativization of paths" - {

    "as a factory method of RelativePath" - {
      behave like relativizationCases(RelativePath.relativize)
    }

    "as a convenience method on AbsolutePath" - {
      behave like relativizationCases(_ relativize _)
    }

    def relativizationCases(relativize: (AbsolutePath, AbsolutePath) => RelativePath): Unit = {

      "successful" in {
        forAll (genAbsolutePath, genRelativePath) { (base, result) =>
          assertResult(result)(relativize(base.resolve(result), base))
        }
      }

      "succeeds if paths are equal" in {
        forAll (genAbsolutePath) { path =>
          assertResult(RelativePath.Empty)(relativize(path, path))
        }
      }

      "fails if base is a child of path" in {
        forAll (genAbsolutePath, genRelativePath) { (path, extension) =>
          whenever (extension.elements.nonEmpty) {
            val base = path.resolve(extension)
            val _ = intercept[IllegalArgumentException](relativize(path, base))
          }
        }
      }

      "fails if base is not a parent of path" in {
        forAll (genAbsolutePath, genAbsolutePath) { (path, base) =>
          whenever (!path.elements.startsWith(base.elements)) {
            val _ = intercept[IllegalArgumentException](relativize(path, base))
          }
        }
      }

    }

  }

}

object PathSpec {

  val genPathElements: Gen[Vector[String]] = {
    val genElement = Gen.nonEmptyListOf(arbitrary[Char]).map(_.mkString)
    Gen.containerOf[Vector, String](genElement).suchThat(validPathElements)
  }

  val genRelativePath: Gen[RelativePath] = {
    genPathElements.map(elements => buildRelativePath(elements))
  }

  val genAbsolutePath: Gen[AbsolutePath] = {
    genPathElements.map(elements => buildAbsolutePath(elements))
  }

  val genPath: Gen[Path] = Gen.oneOf(genRelativePath, genAbsolutePath)

  def validPathElements(elements: Vector[String]): Boolean = {
    elements.forall(e => e.nonEmpty && !e.contains(Path.Separator))
  }

  def buildRelativePath(elements: Vector[String]): RelativePath = {
    RelativePath(elements.mkString(Path.Separator))
  }

  def buildAbsolutePath(elements: Vector[String]): AbsolutePath = {
    AbsolutePath(elements.mkString(Path.Separator, Path.Separator, ""))
  }

}
