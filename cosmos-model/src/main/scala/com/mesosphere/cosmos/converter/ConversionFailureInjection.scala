package com.mesosphere.cosmos.converter

import com.twitter.bijection.{AbstractInjection, Bijection, Injection}

import scala.util.{Failure, Try}

final class ConversionFailureInjection[A, B] private(
  f: Injection[A, B],
  errorMessage: Any => String
) extends AbstractInjection[A, B] {

  import ConversionFailureInjection._

  override def apply(a: A): B = f.apply(a)

  override def invert(b: B): Try[A] = f.invert(b)

  override def andThen[C](g: Injection[B, C]): Injection[A, C] = {
    val composed = super.andThen(overrideFailureMessage(g, errorMessage))
    new ConversionFailureInjection(composed, errorMessage)
  }

  override def andThen[C](bij: Bijection[B, C]): Injection[A, C] = {
    new ConversionFailureInjection(super.andThen(bij), errorMessage)
  }

  override def compose[T](g: Injection[T, A]): Injection[T, B] = {
    val composed = super.compose(overrideFailureMessage(g, errorMessage))
    new ConversionFailureInjection(composed, errorMessage)
  }

  override def compose[T](bij: Bijection[T, A]): Injection[T, B] = {
    new ConversionFailureInjection(super.compose(bij), errorMessage)
  }

}

object ConversionFailureInjection {

  def apply[A, B](f: Injection[A, B])(
    errorMessage: Any => String
  ): ConversionFailureInjection[A, B] = {
    new ConversionFailureInjection(overrideFailureMessage(f, errorMessage), errorMessage)
  }

  private def overrideFailureMessage[A, B](
    f: Injection[A, B],
    errorMessage: Any => String
  ): Injection[A, B] = {
    Injection.build(f.apply)(b => f.invert(b).orElse(Failure(ConversionFailure(errorMessage(b)))))
  }

}
