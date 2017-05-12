package com.mesosphere.universe.v3.model

import com.twitter.util.{Return, Throw, Try}

import java.util.regex.Pattern

object DcosReleaseVersionParser {

  private[this] val versionFragment = "(?:0|[1-9][0-9]*)"
  private[this] val subVersionFragment = "\\." + versionFragment
  private[this] val suffixFragment = "[A-Za-z0-9]+"

  private[v3] val versionRegex = s"^$versionFragment$$"
  private[v3] val suffixRegex = s"^$suffixFragment$$"
  private[v3] val fullRegex = s"^$versionFragment(?:$subVersionFragment)*(?:-$suffixFragment)?$$"

  private[v3] val versionPattern = Pattern.compile(versionRegex)
  private[v3] val suffixPattern = Pattern.compile(suffixRegex)
  private[v3] val fullPattern = Pattern.compile(fullRegex)

  def parseUnsafe(s: String): DcosReleaseVersion = parse(s).get

  def parse(s: String): Try[DcosReleaseVersion] = {
    val errMsg = s"Value '$s' does not conform to expected format $fullRegex"
    Try {
      assert(!s.trim.isEmpty, "Value must not be empty")
      assert(fullPattern.matcher(s).matches(), errMsg)
      s
    } flatMap { validatedString =>
      validatedString.split('-').toList match {
        case version :: suffix :: Nil=>
          Return(version -> Some(suffix))
        case version :: Nil =>
          Return(version -> None)
        case _ =>
          Throw(new AssertionError(errMsg))
      }
    } flatMap { case (version, subVersion) =>
      parseVersionSuffix(version, subVersion, errMsg)
    }
  }

  private[this] def parseVersion(s: String): Try[DcosReleaseVersion.Version] = Try {
    DcosReleaseVersion.Version(s.toInt)
  }

  private[this] def parseSuffix(s: Option[String]): Try[Option[DcosReleaseVersion.Suffix]] = s match {
    case None => Return(None)
    case Some(suff) => Return(Some(DcosReleaseVersion.Suffix(suff)))
  }

  private[this] def parseVersionSuffix(version: String, suffix: Option[String], errMsg: String): Try[DcosReleaseVersion] = {
    version.split('.').toList match {
      case head :: tail :: Nil =>
        for {
          h <- parseVersion(head)
          t <- parseVersion(tail)
          s <- parseSuffix(suffix)
        } yield {
          DcosReleaseVersion(h, List(t), s)
        }
      case head :: Nil =>
        for {
          h <- parseVersion(head)
          s <- parseSuffix(suffix)
        } yield {
          DcosReleaseVersion(h, List.empty, s)
        }
      case head :: tail =>
        for {
          h <- parseVersion(head)
          t <- Try.collect(tail.map(parseVersion))
          s <- parseSuffix(suffix)
        } yield {
          DcosReleaseVersion(h, t.toList, s)
        }
      case _ =>
        Throw(new AssertionError(errMsg))
    }
  }

}
