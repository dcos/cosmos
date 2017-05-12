package com.mesosphere.universe.v3.model

import io.circe.Decoder
import io.circe.Encoder
import io.circe.syntax.EncoderOps

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/dcosReleaseVersion
  */
case class DcosReleaseVersion(
  version: DcosReleaseVersion.Version,
  subVersions: List[DcosReleaseVersion.Version] = List.empty,
  suffix: Option[DcosReleaseVersion.Suffix] = None
) {
  val show: String = {
    val subVersionString: String = subVersions match {
      case Nil =>
        ""
      case head :: Nil =>
        s".${head.value}"
      case head :: tail =>
        s".${head.value}.${tail.map(_.value).mkString(".")}"
    }

    val suffixString: String = suffix match {
      case Some(s) => s"-${s.value}"
      case None    => ""
    }
    version.value.toString + subVersionString + suffixString
  }
}


object DcosReleaseVersion {
  import Ordering.Int
  import scala.language.implicitConversions

  case class Version(value: Int) {
    assert(value >= 0, s"Value $value is not >= 0")
  }
  object Version {
    val zero = Version(0)
    implicit val versionOrdering: Ordering[Version] = Ordering.by(_.value)
  }

  case class Suffix(value: String) {
    assert(
      DcosReleaseVersionParser.suffixPattern.matcher(value).matches(),
      s"Value '$value' does not conform to expected format ${DcosReleaseVersionParser.suffixRegex}"
    )
  }

  implicit val dcosReleaseVersionOrdering: Ordering[DcosReleaseVersion] = new Ordering[DcosReleaseVersion] {
    override def compare(x: DcosReleaseVersion, y: DcosReleaseVersion): Int = {
      val comparisons =
        Version.versionOrdering.compare(x.version, y.version) #::
        x.subVersions.toStream
          .zipAll(y.subVersions, Version.zero, Version.zero)
          .map { case (xx, yy) => Version.versionOrdering.compare(xx, yy) }

      comparisons.find(_ != 0).getOrElse(0)
    }
  }

  implicit def dcosReleaseVersionOrderingOps(left: DcosReleaseVersion): dcosReleaseVersionOrdering.Ops = {
    dcosReleaseVersionOrdering.mkOrderingOps(left)
  }

  implicit val decodeDcosReleaseVersion: Decoder[DcosReleaseVersion] = Decoder.decodeString.map { versionString =>
    DcosReleaseVersionParser.parseUnsafe(versionString)
  }
  implicit val encodeDcosReleaseVersion: Encoder[DcosReleaseVersion] = Encoder.instance(_.show.asJson)

}
