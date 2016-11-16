package com.mesosphere

import org.scalacheck.Gen

object Generators {
  def maxSizedString(maxSize: Int, charGen: Gen[Char]): Gen[String] = for {
    size <- Gen.chooseNum(0, maxSize)
    array <- Gen.containerOfN[Array, Char](size, charGen)
  } yield new String(array)
}
