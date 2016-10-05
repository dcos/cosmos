package com.mesosphere.cosmos.converter

final case class ConversionFailure(message: String) extends RuntimeException(message)
