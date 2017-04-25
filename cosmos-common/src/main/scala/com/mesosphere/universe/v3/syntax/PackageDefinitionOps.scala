package com.mesosphere.universe.v3.syntax

import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.universe
import io.circe.Json
import io.circe.JsonObject
import io.circe.syntax.EncoderOps

final class PackageDefinitionOps(val pkgDef: universe.v4.model.PackageDefinition) extends AnyVal {

  import PackageDefinitionOps._

  def packageCoordinate: PackageCoordinate =  {
    PackageCoordinate(name, version)
  }

  def packagingVersion: universe.v4.model.PackagingVersion = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.packagingVersion
    case v3: universe.v3.model.V3Package => v3.packagingVersion
    case v4: universe.v4.model.V4Package => v4.packagingVersion
  }

  def name: String = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.name
    case v3: universe.v3.model.V3Package => v3.name
    case v4: universe.v4.model.V4Package => v4.name
  }

  def version: universe.v3.model.Version = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.version
    case v3: universe.v3.model.V3Package => v3.version
    case v4: universe.v4.model.V4Package => v4.version
  }

  //noinspection MutatorLikeMethodIsParameterless
  def releaseVersion: universe.v3.model.ReleaseVersion = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.releaseVersion
    case v3: universe.v3.model.V3Package => v3.releaseVersion
    case v4: universe.v4.model.V4Package => v4.releaseVersion
  }

  def maintainer: String = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.maintainer
    case v3: universe.v3.model.V3Package => v3.maintainer
    case v4: universe.v4.model.V4Package => v4.maintainer
  }

  def description: String = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.description
    case v3: universe.v3.model.V3Package => v3.description
    case v4: universe.v4.model.V4Package => v4.description
  }

  def marathon: Option[universe.v3.model.Marathon] = pkgDef match {
    case v2: universe.v3.model.V2Package => Some(v2.marathon)
    case v3: universe.v3.model.V3Package => v3.marathon
    case v4: universe.v4.model.V4Package => v4.marathon
  }

  def tags: List[universe.v3.model.Tag] = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.tags
    case v3: universe.v3.model.V3Package => v3.tags
    case v4: universe.v4.model.V4Package => v4.tags
  }

  def selected: Option[Boolean] = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.selected
    case v3: universe.v3.model.V3Package => v3.selected
    case v4: universe.v4.model.V4Package => v4.selected
  }

  def scm: Option[String]= pkgDef match {
    case v2: universe.v3.model.V2Package => v2.scm
    case v3: universe.v3.model.V3Package => v3.scm
    case v4: universe.v4.model.V4Package => v4.scm
  }

  def website: Option[String] = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.website
    case v3: universe.v3.model.V3Package => v3.website
    case v4: universe.v4.model.V4Package => v4.website
  }

  def framework: Option[Boolean] = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.framework
    case v3: universe.v3.model.V3Package => v3.framework
    case v4: universe.v4.model.V4Package => v4.framework
  }

  def preInstallNotes: Option[String] = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.preInstallNotes
    case v3: universe.v3.model.V3Package => v3.preInstallNotes
    case v4: universe.v4.model.V4Package => v4.preInstallNotes
  }

  def postInstallNotes: Option[String] = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.postInstallNotes
    case v3: universe.v3.model.V3Package => v3.postInstallNotes
    case v4: universe.v4.model.V4Package => v4.postInstallNotes
  }

  def postUninstallNotes: Option[String] = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.postUninstallNotes
    case v3: universe.v3.model.V3Package => v3.postUninstallNotes
    case v4: universe.v4.model.V4Package => v4.postUninstallNotes
  }

  def licenses: Option[List[universe.v3.model.License]] = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.licenses
    case v3: universe.v3.model.V3Package => v3.licenses
    case v4: universe.v4.model.V4Package => v4.licenses
  }

  def config: Option[JsonObject] = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.config
    case v3: universe.v3.model.V3Package => v3.config
    case v4: universe.v4.model.V4Package => v4.config
  }

  def command: Option[universe.v3.model.Command] = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.command
    case v3: universe.v3.model.V3Package => v3.command
    case _: universe.v4.model.V4Package => None // v4 does not have command
  }

  def minDcosReleaseVersion: Option[universe.v3.model.DcosReleaseVersion] = pkgDef match {
    case _: universe.v3.model.V2Package => None
    case v3: universe.v3.model.V3Package => v3.minDcosReleaseVersion
    case v4: universe.v4.model.V4Package => v4.minDcosReleaseVersion
  }

  def v3Resource: Option[universe.v3.model.V3Resource] = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.resource.map {
      case universe.v3.model.V2Resource(assets, images) =>
        universe.v3.model.V3Resource(assets, images)
    }
    case v3: universe.v3.model.V3Package => v3.resource
    case v4: universe.v4.model.V4Package => v4.resource
  }

  def upgradesFrom: Option[List[universe.v3.model.Version]] = pkgDef match {
    case v4: universe.v4.model.V4Package => v4.upgradesFrom
    case _ => None
  }

  def canUpgradeFrom(version: universe.v3.model.Version): Boolean = {
    upgradesFrom.exists(_.exists(v => v == AnyVersion || v == version))
  }

  // -------- Non top-level properties that we are safe to "jump" to --------------

  def images: Option[universe.v3.model.Images] = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.resource.flatMap(_.images)
    case v3: universe.v3.model.V3Package => v3.resource.flatMap(_.images)
    case v4: universe.v4.model.V4Package => v4.resource.flatMap(_.images)
  }

  def cli: Option[universe.v3.model.Cli] = pkgDef match {
    case _ : universe.v3.model.V2Package => None
    case v3: universe.v3.model.V3Package => v3.resource.flatMap(_.cli)
    case v4: universe.v4.model.V4Package => v4.resource.flatMap(_.cli)
  }

  // -------- Utility Methods to convert to Json -----------------------------------
  def resourceJson: Option[Json] = pkgDef match {
    case v2: universe.v3.model.V2Package => v2.resource.map(_.asJson)
    case v3: universe.v3.model.V3Package => v3.resource.map(_.asJson)
    case v4: universe.v4.model.V4Package => v4.resource.map(_.asJson)
  }

}

object PackageDefinitionOps {
  import scala.language.implicitConversions

  val AnyVersion: universe.v3.model.Version = universe.v3.model.Version("*")

  implicit def packageDefinitionToPackageDefinitionOps[P <: universe.v4.model.PackageDefinition](
    pkgDef: P
  ): PackageDefinitionOps = {
    new PackageDefinitionOps(pkgDef)
  }
}
