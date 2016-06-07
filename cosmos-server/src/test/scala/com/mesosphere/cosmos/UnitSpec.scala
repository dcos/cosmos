package com.mesosphere.cosmos

import org.scalatest.{FreeSpec, Inside}
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks

abstract class UnitSpec extends FreeSpec
  with TableDrivenPropertyChecks
  with MockitoSugar
  with Inside
