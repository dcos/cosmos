package com.mesosphere.universe.common

import io.circe.Printer

object JsonUtil {
  val dropNullKeysPrinter: Printer = Printer.noSpaces.copy(dropNullKeys = true)
}
