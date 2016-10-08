package com.mesosphere.universe.v3.syntax

import com.mesosphere.universe.v3.model.PackageDefinition.Tag
import com.mesosphere.universe.v3.model._
import io.circe.JsonObject

final class PackageDefinitionOps(val pkgDef: PackageDefinition) extends AnyVal {

  def packagingVersion: PackagingVersion = pkgDef match {
    case v2: V2Package => v2.packagingVersion
    case v3: V3Package => v3.packagingVersion
  }

  def name: String = pkgDef match {
    case v2: V2Package => v2.name
    case v3: V3Package => v3.name
  }

  def version: PackageDefinition.Version = pkgDef match {
    case v2: V2Package => v2.version
    case v3: V3Package => v3.version
  }

  //noinspection MutatorLikeMethodIsParameterless
  def releaseVersion: PackageDefinition.ReleaseVersion = pkgDef match {
    case v2: V2Package => v2.releaseVersion
    case v3: V3Package => v3.releaseVersion
  }

  def maintainer: String = pkgDef match {
    case v2: V2Package => v2.maintainer
    case v3: V3Package => v3.maintainer
  }

  def description: String = pkgDef match {
    case v2: V2Package => v2.description
    case v3: V3Package => v3.description
  }

  def marathon: Option[Marathon] = pkgDef match {
    case v2: V2Package => Some(v2.marathon)
    case v3: V3Package => v3.marathon
  }

  def tags: List[Tag] = pkgDef match {
    case v2: V2Package => v2.tags
    case v3: V3Package => v3.tags
  }

  def selected: Option[Boolean] = pkgDef match {
    case v2: V2Package => v2.selected
    case v3: V3Package => v3.selected
  }

  def scm: Option[String ]= pkgDef match {
    case v2: V2Package => v2.scm
    case v3: V3Package => v3.scm
  }

  def website: Option[String] = pkgDef match {
    case v2: V2Package => v2.website
    case v3: V3Package => v3.website
  }

  def framework: Option[Boolean] = pkgDef match {
    case v2: V2Package => v2.framework
    case v3: V3Package => v3.framework
  }

  def preInstallNotes: Option[String] = pkgDef match {
    case v2: V2Package => v2.preInstallNotes
    case v3: V3Package => v3.preInstallNotes
  }

  def postInstallNotes: Option[String] = pkgDef match {
    case v2: V2Package => v2.postInstallNotes
    case v3: V3Package => v3.postUninstallNotes
  }

  def postUninstallNotes: Option[String] = pkgDef match {
    case v2: V2Package => v2.postUninstallNotes
    case v3: V3Package => v3.postUninstallNotes
  }

  def licenses: Option[List[License]] = pkgDef match {
    case v2: V2Package => v2.licenses
    case v3: V3Package => v3.licenses
  }

  def config: Option[JsonObject] = pkgDef match {
    case v2: V2Package => v2.config
    case v3: V3Package => v3.config
  }

  def command: Option[Command] = pkgDef match {
    case v2: V2Package => v2.command
    case v3: V3Package => v3.command
  }

  // -------- Non top-level properties that we are save to "jump" to --------------

  def images: Option[Images] = pkgDef match {
    case v2: V2Package => v2.resource.flatMap(_.images)
    case v3: V3Package => v3.resource.flatMap(_.images)
  }

  def cli: Option[Cli] = pkgDef match {
    case v2: V2Package => None
    case v3: V3Package => v3.resource.flatMap(_.cli)
  }

}

object PackageDefinitionOps {
  import scala.language.implicitConversions

  implicit def packageDefinitionToPackageDefinitionOps(pkgDef: PackageDefinition): PackageDefinitionOps = {
    new PackageDefinitionOps(pkgDef)
  }
}
