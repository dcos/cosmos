package com.mesosphere.universe.v3.model

import fastparse.all._
import fastparse.core.Parsed
import fastparse.core.Parser
import scala.util.Failure
import scala.util.Left
import scala.util.Right
import scala.util.Success
import scala.util.Try

// Implements relaxed: http://semver.org/#semantic-versioning-200
final case class SemVer(
  major: Long,
  minor: Long,
  patch: Long,
  preReleases: Seq[Either[String, Long]],
  build: Option[String]
) extends Ordered[SemVer] {

  override def toString(): String = {
    val preReleasePart = if (preReleases.isEmpty) {
      ""
    } else {
      preReleases.map(_.fold(identity, _.toString)).mkString("-", ".", "")
    }

    val buildPart = build.map("+" + _).getOrElse("")

    s"$major.$minor.$patch$preReleasePart$buildPart"
  }

  override def compare(that: SemVer): Int = {
    // Implements: http://semver.org/#spec-item-11
    val comparisons =
      major.compare(that.major) #::
        minor.compare(that.minor) #::
        patch.compare(that.patch) #::
        comparePreReleases(that.preReleases)

    comparisons.find(_ != 0).getOrElse(0)
  }

  private[this] def comparePreReleases(
    otherPreReleases: Seq[Either[String, Long]]
  ): Stream[Int] = {
    if (preReleases.isEmpty && otherPreReleases.nonEmpty) {
      1 #:: Stream.empty
    } else if (preReleases.nonEmpty && otherPreReleases.isEmpty) {
      -1 #:: Stream.empty
    } else {
      preReleases.toStream.zipAll(otherPreReleases, Right(-1L), Right(-1L)).map {
        case (Left(a), Left(b)) => a.compare(b)
        case (Right(a), Right(b)) => a.compare(b)
        case (Left(_), Right(_)) => 1
        case (Right(_), Left(_)) => -1
      }
    }
  }

}

object SemVer {
  private[this] val versionParser = {
    val alpha = CharIn(('a' to 'z') ++ ('A' to 'Z')).!
    val digit = CharIn('0' to '9').!
    val number = digit.rep(1).!.map(_.toLong)

    val major = number
    val other = ("." ~ number).?.map(_.getOrElse(0L)) // Assume 0 if not specified

    // Implements: http://semver.org/#spec-item-9
    val preRelease = {
      val preReleasePart: Parser[Either[String, Long], Char, String] = {
        (alpha | digit | "-").rep(1).!.map { value =>
          Try(value.toLong) match {
            case Success(long) => Right(long)
            case Failure(_) => Left(value)
          }
        }
      }

      ("-" ~ preReleasePart.rep(min=1, sep=".")).?.map {
        case Some(parts) => parts
        case None => Seq.empty
      }
    }

    // Implements: http://semver.org/#spec-item-10
    val build = ("+" ~ (alpha | digit | ".").rep(1).!).?

    (Start ~ major ~ other ~ other ~ preRelease ~ build ~ End).map {
      case (major, minor, patch, preReleases, build) =>
        SemVer(major, minor, patch, preReleases, build)
    }
  }

  def apply(value: String): Option[SemVer] = {
    versionParser.parse(value) match {
      case Parsed.Success(version, _) => Some(version)
      case _ => None
    }
  }
}
