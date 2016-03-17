package com.mesosphere.cosmos

import org.scalatest.FreeSpec
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks

abstract class UnitSpec extends FreeSpec with TableDrivenPropertyChecks with MockitoSugar
