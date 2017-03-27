package com.mesosphere.universe.v3.model

import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import io.circe.JsonObject

sealed abstract class PackageDefinition
object PackageDefinition {

  final class ReleaseVersion private(val value: Long) extends AnyVal

  object ReleaseVersion {

    def apply(value: Long): Try[ReleaseVersion] = {
      if (value >= 0) {
        Return(new ReleaseVersion(value))
      } else {
        val message = s"Expected integer value >= 0 for release version, but found [$value]"
        Throw(new IllegalArgumentException(message))
      }
    }

    implicit val packageDefinitionReleaseVersionOrdering: Ordering[ReleaseVersion] = {
      Ordering.by(_.value)
    }

  }

  implicit val packageDefinitionOrdering = new Ordering[PackageDefinition] {
    override def compare(a: PackageDefinition, b: PackageDefinition): Int = {
      PackageDefinition.compare(
        (a.name, a.version, a.releaseVersion),
        (b.name, b.version, b.releaseVersion)
      )
    }
  }

  def compare(
    a: (String, Version, ReleaseVersion),
    b: (String, Version, ReleaseVersion)
  ): Int = {
    val (aName, _, aReleaseVersion) = a
    val (bName, _, bReleaseVersion) = b

    val orderName = aName.compare(bName)
    if (orderName != 0) {
      orderName
    } else {
      // Use release version
      aReleaseVersion.value.compare(bReleaseVersion.value)
    }
  }
}

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/v20Package
  */
case class V2Package(
  packagingVersion: V2PackagingVersion.type = V2PackagingVersion,
  name: String,
  version: Version,
  releaseVersion: PackageDefinition.ReleaseVersion,
  maintainer: String,
  description: String,
  marathon: Marathon,
  tags: List[Tag] = Nil,
  selected: Option[Boolean] = None,
  scm: Option[String] = None,
  website: Option[String] = None,
  framework: Option[Boolean] = None,
  preInstallNotes: Option[String] = None,
  postInstallNotes: Option[String] = None,
  postUninstallNotes: Option[String] = None,
  licenses: Option[List[License]] = None,
  resource: Option[V2Resource] = None,
  config: Option[JsonObject] = None,
  command: Option[Command] = None
) extends PackageDefinition with Ordered[V2Package] {
  override def compare(that: V2Package): Int = {
    PackageDefinition.compare(
      (name, version, releaseVersion),
      (that.name, that.version, that.releaseVersion)
    )
  }
}

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/v30Package
  */
case class V3Package(
  packagingVersion: V3PackagingVersion.type = V3PackagingVersion,
  name: String,
  version: Version,
  releaseVersion: PackageDefinition.ReleaseVersion,
  maintainer: String,
  description: String,
  tags: List[Tag] = Nil,
  selected: Option[Boolean] = None,
  scm: Option[String] = None,
  website: Option[String] = None,
  framework: Option[Boolean] = None,
  preInstallNotes: Option[String] = None,
  postInstallNotes: Option[String] = None,
  postUninstallNotes: Option[String] = None,
  licenses: Option[List[License]] = None,
  minDcosReleaseVersion: Option[DcosReleaseVersion] = None,
  marathon: Option[Marathon] = None,
  resource: Option[V3Resource] = None,
  config: Option[JsonObject] = None,
  command: Option[Command] = None
) extends PackageDefinition with Ordered[V3Package] {
  override def compare(that: V3Package): Int = {
    PackageDefinition.compare(
      (name, version, releaseVersion),
      (that.name, that.version, that.releaseVersion)
    )
  }
}
