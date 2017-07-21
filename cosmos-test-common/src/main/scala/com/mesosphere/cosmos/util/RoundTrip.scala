package com.mesosphere.cosmos.util

import scala.language.implicitConversions
import shapeless._

class RoundTrip[A] private(
  private val withResource: (A => Unit) => Unit
) {

  def flatMap[B](transform: A => RoundTrip[B]): RoundTrip[B] = {
    def withResourceB(bInner: B => Unit): Unit = {
      withResource { a: A =>
        transform(a).withResource { b: B =>
          bInner(b)
        }
      }
    }

    new RoundTrip[B](withResourceB)
  }

  def apply[B](transform: A => B): B = {
    map(transform).run()
  }

  def run(): A = {
    var innerA: Option[A] = None
    withResource { a: A =>
      innerA = Some(a)
    }
    innerA.get
  }

  def hList(): RoundTrip[A :: HNil] = {
    map(_ :: HNil)
  }

  def map[B](transform: A => B): RoundTrip[B] = {
    def withResourceB(bInner: B => Unit): Unit = {
      withResource { a: A =>
        bInner(transform(a))
      }
    }

    new RoundTrip[B](withResourceB)
  }

}

object RoundTrip {

  def apply[A](a: => A): RoundTrip[A] = {
    RoundTrip(a, (_: A) => ())
  }

  def apply[A](
    forward: => A,
    backwards: A => Unit
  ): RoundTrip[A] = {
    def withResource(aInner: A => Unit): Unit = {
      val a = forward
      try {
        aInner(a)
      } finally {
        backwards(a)
      }
    }

    new RoundTrip[A](withResource)
  }

  implicit class RoundTripHListOps[B <: HList](self: => RoundTrip[B]) {
    def &:[A](other: RoundTrip[A]): RoundTrip[A :: B] = {
      other.flatMap { a =>
        self.map(a :: _)
      }
    }
  }

  implicit class RoundTripOps[B](self: => RoundTrip[B]) {
    def &:[A](other: RoundTrip[A]): RoundTrip[A :: B :: HNil] = {
      other.flatMap { a =>
        self.map(b => a :: b :: HNil)
      }
    }
  }

}
