package com.mesosphere.cosmos.converter

// TODO(version): Can this be given more structure (e.g. name and type of field that failed, package, etc.)?
final case class ConversionFailure(message: String) extends RuntimeException(message)
