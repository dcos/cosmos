package com.mesosphere.cosmos.thirdparty.marathon.model

import com.mesosphere.cosmos.circe.Decoders.decode64
import com.mesosphere.cosmos.circe.Decoders.parse64
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.model.StorageEnvelope
import com.mesosphere.cosmos.model.PackageOrigin
import com.mesosphere.universe
import com.netaporter.uri.Uri
import io.circe.JsonObject
import scala.util.Try

/** Partial Marathon AppDefinition.
 *
 *  This is not a full Marathon AppDefinition. Marathon's AppDefinition is a moving target.
 *  We should only decode the parts that Cosmos cares about.
 *  This is okay as long as we don't have an encoder for this class.
 */
case class MarathonApp(
  id: AppId,
  labels: Map[String, String]
)

object MarathonApp {
  val frameworkNameLabel = "DCOS_PACKAGE_FRAMEWORK_NAME"
  val metadataLabel = "DCOS_PACKAGE_METADATA"
  val nameLabel = "DCOS_PACKAGE_NAME"
  val repositoryLabel = "DCOS_PACKAGE_SOURCE"
  val versionLabel = "DCOS_PACKAGE_VERSION"
  val optionsLabel = "DCOS_PACKAGE_OPTIONS"
  val packageLabel = "DCOS_PACKAGE_DEFINITION"

  implicit final class Ops(val app: MarathonApp) extends AnyVal {

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
