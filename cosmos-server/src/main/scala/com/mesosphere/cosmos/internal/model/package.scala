package com.mesosphere.cosmos.internal

import com.mesosphere.cosmos.circe.Decoders.decode64
import com.mesosphere.cosmos.circe.Decoders.parse64
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.cosmos.model.StorageEnvelope
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.bijection.Injection
import io.circe.JsonObject
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.util.Try

package object model {
  // TODO: Move this to the companion object for MarathonApp
  implicit final class MarathonAppOps(val app: MarathonApp) extends AnyVal {

    def packageName: Option[String] = app.labels.get(MarathonApp.nameLabel)

    def packageVersion: Option[universe.v3.model.Version] = {
      app.labels.get(MarathonApp.versionLabel).map(universe.v3.model.Version(_))
    }

    def packageRepository: Option[PackageOrigin] = for {
      repoValue <- app.labels.get(MarathonApp.repositoryLabel)
      originUri <- Try(Uri.parse(repoValue)).toOption
    } yield PackageOrigin(originUri)

    def packageMetadata: Option[label.v1.model.PackageMetadata] = {
      app.labels.get(MarathonApp.metadataLabel).map { string =>
        decode64[label.v1.model.PackageMetadata](string)
      }
    }

    def packageDefinition: Option[universe.v4.model.PackageDefinition] = {
      app.labels.get(MarathonApp.packageLabel).map { string =>
        decode64[StorageEnvelope](string).decodeData[universe.v4.model.PackageDefinition]
      }
    }

    def serviceOptions: Option[JsonObject] = {
      app.labels.get(MarathonApp.optionsLabel).flatMap { string =>
        parse64(string).asObject
      }
    }

  }

}
