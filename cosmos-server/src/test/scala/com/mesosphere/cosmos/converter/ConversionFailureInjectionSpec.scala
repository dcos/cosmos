package com.mesosphere.cosmos.converter

import com.twitter.bijection.Bijection.symbol2String
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.{Bijection, Conversion, Injection}
import org.scalatest.FreeSpec

import scala.util.{Failure, Success, Try}

final class ConversionFailureInjectionSpec extends FreeSpec {

  "A ConversionFailureInjection should" - {

    implicit val customIntToString = ConversionFailureInjection(implicitly[Injection[Int, String]]) {
      case ba: Array[_] => s"Can't handle ${ba.map(_.toString).mkString("[", ", ", "]")}"
      case x => s"Can't handle $x"
    }

    implicit val customSetToList =
      ConversionFailureInjection(implicitly[Injection[Set[Char], List[Char]]])(x => s"Not possible: $x")

    "for Int => String" - {
      behave like applyAndInvertTests(1337, "1337", "foo", "Can't handle foo")
    }

    "for Set[Char] => List[Char]" - {
      behave like applyAndInvertTests(
        successA = Set('a', 'b', 'c'),
        successB = List('a', 'b', 'c'),
        failureB = List('a', 'b', 'c', 'a'),
        failureMessage = "Not possible: List(a, b, c, a)"
      )
    }

    "override the andThen(Injection) method" - {

      implicit val intToStringToByteArray =
        implicitly[Injection[Int, String]] andThen implicitly[Injection[String, Array[Byte]]]

      behave like chainingTests(
        leftValue = 1337,
        rightValueThatCanConvertToLeftValue = Array[Byte](49, 51, 51, 55),
        rightValueThatFailsToConvertToLeftValue = Array[Byte](102, 111, 111),
        middleValueStringForFailedConversionToLeftValue = "foo"
      )

      "by propagating the custom error message for the (right => middle) step when chained" in {
        assertResult(Failure(ConversionFailure("Can't handle [-64]"))) {
          Array[Byte](-64).as[Try[Int]]
        }
      }

    }

    "override the andThen(Bijection) method" - {

      implicit val intToStringToSymbol =
        implicitly[Injection[Int, String]] andThen implicitly[Bijection[Symbol, String]].inverse

      behave like chainingTests(
        leftValue = 1337,
        rightValueThatCanConvertToLeftValue = Symbol("1337"),
        rightValueThatFailsToConvertToLeftValue = 'foo,
        middleValueStringForFailedConversionToLeftValue = "foo"
      )

    }

    "override the compose(Injection) method" - {

      implicit val shortToIntToString =
        implicitly[Injection[Int, String]] compose implicitly[Injection[Short, Int]]

      behave like chainingTests(
        leftValue = 1337.toShort,
        rightValueThatCanConvertToLeftValue = "1337",
        rightValueThatFailsToConvertToLeftValue = "65536",
        middleValueStringForFailedConversionToLeftValue = "65536"
      )

      "by propagating the custom error message for the (right => middle) step when chained" in {
        assertResult(Failure(ConversionFailure("Can't handle foo"))) {
          "foo".as[Try[Int]]
        }
      }

    }

    "override the compose(Bijection) method" - {

      implicit val floatToIntToString =
        implicitly[Injection[Int, String]] compose Bijection.float2IntIEEE754

      behave like chainingTests(
        leftValue = 0.0f,
        rightValueThatCanConvertToLeftValue = "0",
        rightValueThatFailsToConvertToLeftValue = "foo",
        middleValueStringForFailedConversionToLeftValue = "foo"
      )

    }

  }

  private[this] def applyAndInvertTests[A, B](
    successA: A,
    successB: B,
    failureB: B,
    failureMessage: String
  )(implicit aToB: Conversion[A, B], bToA: Conversion[B, Try[A]]): Unit = {

    "delegate to its Injection for the forward direction" - {
      assertResult(successB)(successA.as[B])
    }

    "delegate to its Injection for the reverse direction" - {

      "and preserve the success behavior" - {
        assertResult(Success(successA))(successB.as[Try[A]])
      }

      "but override the failure behavior by returning a ConversionFailure with a custom message" - {
        assertResult(Failure(ConversionFailure(failureMessage)))(failureB.as[Try[A]])
      }

    }

  }

  private[this] def chainingTests[A, B, C](
    leftValue: A,
    rightValueThatCanConvertToLeftValue: C,
    rightValueThatFailsToConvertToLeftValue: C,
    middleValueStringForFailedConversionToLeftValue: String
  )(implicit aToC: Injection[A, C]): Unit = {

    "by delegating to the superclass for the forward direction" in {
      assertResult(rightValueThatCanConvertToLeftValue)(leftValue.as[C])
    }

    "by delegating to the superclass for the reverse direction when successful" in {
      assertResult(Success(leftValue))(rightValueThatCanConvertToLeftValue.as[Try[A]])
    }

    "by propagating the custom error message for the (middle => left) step when chained" in {
      val message = s"Can't handle $middleValueStringForFailedConversionToLeftValue"
      assertResult(Failure(ConversionFailure(message))) {
        rightValueThatFailsToConvertToLeftValue.as[Try[A]]
      }
    }

    "and return another ConversionFailureInjection instance" in {
      assert(implicitly[Injection[A, C]].isInstanceOf[ConversionFailureInjection[_, _]])
    }

    "the chained instance can itself be chained correctly" in {
      val rightToAny =
        Injection.build[C, Any](identity)(_ => Failure(new Exception("Doesn't matter")))
      implicit val leftToAny = implicitly[Injection[A, C]] andThen rightToAny

      assertResult(Failure(ConversionFailure("Can't handle ()")))(((): Any).as[Try[A]])
    }

  }

}
