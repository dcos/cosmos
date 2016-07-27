package com.mesosphere.cosmos

import com.mesosphere.cosmos
import org.scalatest.FreeSpec
import com.twitter.util.{Try,Return,Throw}

final class TrysSpec extends FreeSpec {

  "join[A,B]" in {
    assertResult(Return((1,2)))(Trys.join(Return(1), Return(2)))
    val e = new IllegalArgumentException
    val n = new NoSuchElementException
    assertResult(Throw(e))(Trys.join(Throw(e), Return(2)))
    assertResult(Throw(e))(Trys.join(Return(1), Throw(e)))
    assertResult(Throw(n))(Trys.join(Throw(n), Throw(e)))
  }

  "join[A,B,C]" in {
    assertResult(Return((1,2,3)))(Trys.join(Return(1), Return(2), Return(3)))
    val e = new IllegalArgumentException
    val n = new NoSuchElementException
    assertResult(Throw(e))(Trys.join(Throw(e), Return(2), Return(3)))
    assertResult(Throw(e))(Trys.join(Return(1), Throw(e), Return(3)))
    assertResult(Throw(n))(Trys.join(Throw(n), Throw(e), Return(3)))
  }
}
