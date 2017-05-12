package com.mesosphere.util

class RoundTrip[A](
  private val withResource: (A => Unit) => Unit
) {

  def map[B](transform: A => B): RoundTrip[B] = {
    def withResourceB(bInner: B => Unit): Unit = {
      withResource { a: A =>
        bInner(transform(a))
      }
    }
    new RoundTrip[B](withResourceB)
  }

  def flatMap[B](transform: A => RoundTrip[B]): RoundTrip[B] = {
    def withResourceB(bInner: B => Unit): Unit = {
      withResource{ a: A =>
        transform(a).withResource { b: B =>
          bInner(b)
        }
      }
    }
    new RoundTrip[B](withResourceB)
  }

  def get(): A = {
    var innerA: Option[A] = None
    withResource { a: A =>
      innerA = Some(a)
    }
    innerA.get
  }

  def apply[B](transform: A => B): B = {
    map(transform).get()
  }

}

object RoundTrip {

  def apply[A](
    forward: => A)(
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

  def value[A](a: A): RoundTrip[A] = {
    RoundTrip(a)((_: A) => ())
  }

}
