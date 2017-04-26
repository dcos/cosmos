package com.mesosphere.universe.v3.model

import io.circe.Decoder
import io.circe.Encoder

sealed trait VersionSpecification {

  final def matches(version: Version): Boolean = {
    this match {
      case AnyVersion => true
      case ExactVersion(v) => version == v
    }
  }

}

object VersionSpecification {

  val Wildcard: String = "*"

  implicit val decode: Decoder[VersionSpecification] = {
    implicitly[Decoder[String]].flatMap {
      case Wildcard => Decoder.const(AnyVersion)
      case _ => implicitly[Decoder[Version]].map(ExactVersion)
    }
  }

  implicit val encode: Encoder[VersionSpecification] = {
    implicitly[Encoder[String]].contramap {
      case AnyVersion => Wildcard
      case ExactVersion(v) => v.toString
    }
  }

}

case object AnyVersion extends VersionSpecification
case class ExactVersion(version: Version) extends VersionSpecification
