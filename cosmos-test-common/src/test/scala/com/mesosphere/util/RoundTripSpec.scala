package com.mesosphere.util

import com.mesosphere.cosmos.util.RoundTrip
import org.mockito.InOrder
import org.mockito.Mockito._
import org.mockito._
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar

class RoundTripSpec extends FreeSpec with Matchers with MockitoSugar {

  import RoundTripSpec._

  "RoundTrip.lift(exp)" - {
    val i: Int = 3
    "should be map able" in {
      RoundTrip.lift(i).map(_ * i).run().
        shouldBe(i * i)
    }

    "should be flatMap able" in {
      RoundTrip.lift(i).flatMap(i => RoundTrip.lift(i * i)).run().
        shouldBe(i * i)
    }

    "should not throw error if map fails but is not evaluated" in {
      RoundTrip.lift(i).map(_ => throw new Error("this should not happen"))
    }

    "throw error if map fails" in {
      assertThrows[Error] {
        RoundTrip.lift(i).map(_ => throw new Error("this should happen")).run()
      }
    }

    "should not throw error if flatMap fails but is not evaluated" in {
      RoundTrip.lift(i).flatMap(i => RoundTrip.lift(i / 0))
    }

    "should throw error if flatMap fails when evaluated" in {
      assertThrows[ArithmeticException] {
        RoundTrip.lift(i).flatMap(i => RoundTrip.lift(i / 0)).run()
      }
    }
  }

  "RoundTrip run() should return inner value" in {
    val aVal = 22
    val doer = mock[Doer]
    when(doer.doA()).thenReturn(aVal)
    val order: InOrder = Mockito.inOrder(doer)
    doA(doer).run() shouldBe aVal
    order.verify(doer, times(1)).doA()
    order.verify(doer, times(1)).undoA(aVal)
  }

  "RoundTrip should map" in {
    val aVal = 22
    val doer = mock[Doer]
    when(doer.doA()).thenReturn(aVal)
    val order: InOrder = Mockito.inOrder(doer)
    doA(doer).map(_ ^ 2).run() shouldBe aVal ^ 2
    order.verify(doer, times(1)).doA()
    order.verify(doer, times(1)).undoA(aVal)
  }

  "RoundTrip should flatMap" in {
    val aVal = 22
    val bVal = "hello"
    val doer = mock[Doer]
    when(doer.doA()).thenReturn(aVal)
    when(doer.doB()).thenReturn(bVal)
    val order: InOrder = Mockito.inOrder(doer)
    doA(doer).flatMap(_ => doB(doer)).run() shouldBe bVal
    order.verify(doer, times(1)).doA()
    order.verify(doer, times(1)).doB()
    order.verify(doer, times(1)).undoB(bVal)
    order.verify(doer, times(1)).undoA(aVal)
  }

  "RoundTrip should revert state even if map throws error" in {
    val aVal = 22
    val doer = mock[Doer]
    when(doer.doA()).thenReturn(aVal)
    val order: InOrder = Mockito.inOrder(doer)
    assertThrows[Error] {
      doA(doer).map { _ =>
        throw new Error("this should throw")
      }.run()
    }
    order.verify(doer, times(1)).doA()
    order.verify(doer, times(1)).undoA(aVal)
  }

  "RoundTrip should revert state even if flatMap fails" in {
    val aVal = 22
    val doer = mock[Doer]
    when(doer.doA()).thenReturn(aVal)
    val order: InOrder = Mockito.inOrder(doer)
    assertThrows[Error] {
      doA(doer).flatMap { _ =>
        throw new Error("this should throw")
      }.run()
    }
    order.verify(doer, times(1)).doA()
    order.verify(doer, times(1)).undoA(aVal)
  }

  "RoundTrip should clean up partially executed maps and flatMaps" in {
    val aVal = 22
    val bVal = "hello"
    val cVal = '0'
    val doer = mock[Doer]
    when(doer.doA()).thenReturn(aVal)
    when(doer.doB()).thenReturn(bVal)
    when(doer.doC()).thenReturn(cVal)
    val order: InOrder = Mockito.inOrder(doer)
    val zero = 0
    assertThrows[ArithmeticException] {
      doA(doer)
        .flatMap(_ => doB(doer))
        .map(_ => 42 / zero)
        .flatMap(_ => doC(doer))
        .run()
    }
    order.verify(doer, times(1)).doA()
    order.verify(doer, times(1)).doB()
    order.verify(doer, never()).doC()
    order.verify(doer, never()).undoC(cVal)
    order.verify(doer, times(1)).undoB(bVal)
    order.verify(doer, times(1)).undoA(aVal)
  }

  "RoundTrip should observe the forward context inside runWith" in {
    val innerFoo = 3
    val outerFoo = 0
    var foo = outerFoo
    val changeA: RoundTrip[Int] = RoundTrip {
      val oldFoo = foo
      foo = innerFoo
      oldFoo
    } { old =>
      foo = old
    }
    changeA.runWith { _ =>
      assert(foo == innerFoo)
    }
    assert(foo == outerFoo)
  }

  "RoundTrip should be somewhat flat" in {
    val aVal = 22
    val bVal = "hello"
    val cVal = '0'
    val doer = mock[Doer]
    when(doer.doA()).thenReturn(aVal)
    when(doer.doB()).thenReturn(bVal)
    when(doer.doC()).thenReturn(cVal)
    val order: InOrder = Mockito.inOrder(doer)
    val zero = 0
    assertThrows[ArithmeticException] {
      (doA(doer) &:
        doB(doer).map(_ => 42 / zero) &:
        doC(doer)).run()
    }
    order.verify(doer, times(1)).doA()
    order.verify(doer, times(1)).doB()
    order.verify(doer, never()).doC()
    order.verify(doer, never()).undoC(cVal)
    order.verify(doer, times(1)).undoB(bVal)
    order.verify(doer, times(1)).undoA(aVal)
  }

}

object RoundTripSpec {

  def doA(doer: Doer): RoundTrip[Int] = RoundTrip(doer.doA())(doer.undoA)

  def doB(doer: Doer): RoundTrip[String] = RoundTrip(doer.doB())(doer.undoB)

  def doC(doer: Doer): RoundTrip[Char] = RoundTrip(doer.doC())(doer.undoC)

  trait Doer {
    def doA(): Int

    def undoA(i: Int): Unit

    def doB(): String

    def undoB(s: String): Unit

    def doC(): Char

    def undoC(c: Char): Unit
  }

}

