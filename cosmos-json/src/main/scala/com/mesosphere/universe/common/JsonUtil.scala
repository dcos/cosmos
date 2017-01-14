package com.mesosphere.universe.common

import io.circe.Decoder
import io.circe.Error
import io.circe.Printer
import io.circe.jawn.decode
import java.nio.charset.StandardCharsets
import java.util.Base64

object JsonUtil {

  def decode64[A: Decoder](str: String): Either[Error,A] = {
    decode[A](new String(Base64.getDecoder.decode(str),StandardCharsets.UTF_8))
  }
  val dropNullKeysPrinter: Printer = Printer.noSpaces.copy(dropNullKeys = true)
}
