package com.mesosphere.cosmos.internal

import com.mesosphere.cosmos.circe.Decoders.decode64
import com.mesosphere.cosmos.circe.Decoders.parse64
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.label.v1.circe.Decoders._
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.bijection.Injection
import io.circe.JsonObject
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.util.Try

package object model {

  implicit final class MarathonAppOps(val app: MarathonApp) extends AnyVal {

    def packageName: Option[String] = app.labels.get(MarathonApp.nameLabel)

    def packageVersion: Option[universe.v3.model.Version] = {
      app.labels.get(MarathonApp.versionLabel).map(universe.v3.model.Version(_))
    }

    def packageRepository: Option[PackageOrigin] = for {
      repoValue <- app.labels.get(MarathonApp.repositoryLabel)
      originUri <- Try(Uri.parse(repoValue)).toOption
    } yield PackageOrigin(originUri)

    // TODO: This needs to be optional in the next PR when we persist the PkgDef envelope
    def packageMetadata: label.v1.model.PackageMetadata = {
      decode64[label.v1.model.PackageMetadata](
        app.labels.get(MarathonApp.metadataLabel).getOrElse("")
      )
    }

    def serviceOptions: Option[JsonObject] = {
      app.labels.get(MarathonApp.optionsLabel).flatMap { string =>
        parse64(string).asObject
      }
    }

  }

}