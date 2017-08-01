package com.mesosphere.util

import com.mesosphere.cosmos.util.RoundTrip
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import shapeless._

class RoundTripSpec extends FreeSpec with Matchers {

  "RoundTrip.apply(value)" - {
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

  // TODO: make this independent
  var state: Int = 0
  def forward(newState : Int): Int = {
    val oldState = state
    state = newState
    oldState
  }
  def backwards(previous: Int): Unit = {
    state = previous
  }
  def withChangedState(newValue: Int): RoundTrip[Int] = {
    RoundTrip(forward(newValue))(backwards).map(_ => newValue)
  }
  def withIncrement(): RoundTrip[Int] = {
    //RoundTrip.value(state).flatMap { s =>
    //  withChangedState(s + 1)
    //}
    withChangedState(state + 1)
  }

  "RoundTrip run() should return inner value" in {
    val i = 3
    withChangedState(i).run()
      .shouldBe(i)
    state shouldBe 0
  }

  "RoundTrip should map" in {
    val i = 3
    withChangedState(i).map(_ * i).run()
      .shouldBe(i * i)
    state shouldBe 0
  }

  "RoundTrip should flatMap" in {
    val i = 3
    withChangedState(i).flatMap(i => withChangedState(i * i)).run()
      .shouldBe(i * i)
    state shouldBe 0
  }

  "RoundTrip should revert state even if map throws error" in {
    val i = 3
    intercept[Error] {
      withChangedState(i).map { i =>
        state shouldBe i
        throw new Error("This should go back to original state")
      }.run()
    }
    state shouldBe 0
  }

  "RoundTrip should revert state even if flatMap fails" in {
    val i = 3
    intercept[Error] {
      withChangedState(i).flatMap { i =>
        state shouldBe i
        throw new Error("This should go back to original state")
      }.run()
    }
    state shouldBe 0
  }

  "RoundTrip should be able to use for comprehensions" in {
    val with3 = for {
      _ <- withIncrement()
      _ <- withIncrement()
      i <- withIncrement()
    } yield i
    with3.run() shouldBe 3
  }

  "RoundTrip.apply should clean up even if flatMap fails" +
    " after more maps have been added. Other maps should not be executed" in {
    val result = for {
      _ <- withIncrement()
      _ <- withIncrement()
      i <- withIncrement()
      r <- withChangedState(i / 0)
      _ <- withIncrement()
    } yield r
    intercept[ArithmeticException] {
      result.run()
    }
    state shouldBe 0
  }

  "RoundTrip should observe the forward context inside map and flatMap" in {
    val a = 1
    val b = -2
    val c = 8
    withIncrement().flatMap { _ =>
      state shouldBe a
      withChangedState(b).flatMap { _ =>
        state shouldBe b
        withChangedState(c).map { _ =>
          state shouldBe c
        }
      }
    }.run()
    state shouldBe 0
  }

  "RoundTrip should be able to be used like a IOC function" in {
    val i = 3
      withChangedState(i).runWith { j =>
        j shouldBe i
      }
    }

    "RoundTrip should be somewhat flat" in {
      val withRt = withIncrement().map(_.toString) &: withIncrement().map(_.toFloat) &: withIncrement()
      withRt.run() shouldBe ("1" :: 2.0 :: 3 :: HNil)

      assertThrows[Error] {
        withRt.runWith { case (_: String) :: (_: Float) :: (_: Int) :: HNil =>
        throw new Error("This should throw")
      }
    }
  }

}

