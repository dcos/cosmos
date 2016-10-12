package com.mesosphere.cosmos.internal.model

import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.twitter.bijection.Injection

class MarathonAppOps(val app: MarathonApp) extends AnyVal {
  def packageName: Option[String] = app.labels.get(MarathonApp.nameLabel)

  def packageReleaseVersion: Option[universe.v3.model.PackageDefinition.ReleaseVersion] = {
    app.labels.get(MarathonApp.releaseLabel).flatMap { label =>
      Injection.invert[universe.v3.model.PackageDefinition.ReleaseVersion, String](label).toOption
    }
  }

  def packageVersion: Option[universe.v3.model.PackageDefinition.Version] = {
    app.labels.get(MarathonApp.versionLabel).map(universe.v3.model.PackageDefinition.Version)
  }

  def packageRepository: Option[String] = app.labels.get(MarathonApp.repositoryLabel)

  def packageMetadata: Option[String] = app.labels.get(MarathonApp.metadataLabel)

}

object MarathonAppOps {
  import scala.language.implicitConversions

  implicit def marathonAppToMarathonAppOps(app: MarathonApp): MarathonAppOps = {
    new MarathonAppOps(app)
  }

}
