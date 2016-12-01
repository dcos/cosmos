package com.mesosphere.cosmos.internal

import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.netaporter.uri.Uri
import com.twitter.bijection.Injection
import scala.util.Try

package object model {
  implicit final class MarathonAppOps(val app: MarathonApp) extends AnyVal {
    def packageName: Option[String] = app.labels.get(MarathonApp.nameLabel)

    def packageReleaseVersion: Option[universe.v3.model.PackageDefinition.ReleaseVersion] = {
      app.labels.get(MarathonApp.releaseLabel).flatMap { label =>
        Injection.invert[universe.v3.model.PackageDefinition.ReleaseVersion, String](
          label
        ).toOption
      }
    }

    def packageVersion: Option[universe.v3.model.PackageDefinition.Version] = {
      app.labels.get(MarathonApp.versionLabel).map(universe.v3.model.PackageDefinition.Version)
    }

    def packageRepository: Option[PackageOrigin] = for {
      repoValue <- app.labels.get(MarathonApp.repositoryLabel)
      originUri <- Try(Uri.parse(repoValue)).toOption
    } yield PackageOrigin(originUri)

    def packageMetadata: Option[String] = app.labels.get(MarathonApp.metadataLabel)

  }
}
