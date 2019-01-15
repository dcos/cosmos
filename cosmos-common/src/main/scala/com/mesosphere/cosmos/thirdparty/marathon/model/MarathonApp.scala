package com.mesosphere.cosmos.thirdparty.marathon.model

import com.mesosphere.cosmos.circe.Decoders.decode64
import com.mesosphere.cosmos.circe.Decoders.parse64
import com.mesosphere.cosmos.model.PackageOrigin
import com.mesosphere.cosmos.model.StorageEnvelope
import com.mesosphere.error.ResultOps
import com.mesosphere.universe
import com.netaporter.uri.Uri
import io.circe.Decoder
import io.circe.JsonObject
import io.circe.generic.semiauto._
import scala.util.Try

/** Partial Marathon AppDefinition.
 *
 *  This is not a full Marathon AppDefinition. Marathon's AppDefinition is a moving target.
 *  We should only decode the parts that Cosmos cares about. That is the `id` and the `labels`.
 *  This is okay as long as we don't have an encoder for this class.
 */
case class MarathonApp(
  id: AppId,
  env: Option[Map[String, String]],
  labels: Option[Map[String, String]]
)

object MarathonApp {
  val frameworkNameLabel = "DCOS_PACKAGE_FRAMEWORK_NAME"
  val nameLabel = "DCOS_PACKAGE_NAME"
  val repositoryLabel = "DCOS_PACKAGE_SOURCE"
  val versionLabel = "DCOS_PACKAGE_VERSION"
  val optionsLabel = "DCOS_PACKAGE_OPTIONS"
  val packageLabel = "DCOS_PACKAGE_DEFINITION"

  implicit val decodeMarathonApp: Decoder[MarathonApp] = deriveDecoder[MarathonApp]

  implicit final class Ops(val app: MarathonApp) extends AnyVal {

    def getLabel(key: String):Option[String] = app.labels.flatMap(_.get(key))

    def getEnv(key: String):Option[String] = app.env.flatMap(_.get(key))

    def packageName: Option[String] = getLabel(MarathonApp.nameLabel)

    def packageVersion: Option[universe.v3.model.Version] = {
      getLabel(MarathonApp.versionLabel).map(universe.v3.model.Version(_))
    }

    def packageRepository: Option[PackageOrigin] = for {
      repoValue <- getLabel(MarathonApp.repositoryLabel)
      originUri <- Try(Uri.parse(repoValue)).toOption
    } yield PackageOrigin(originUri)

    def packageDefinition: Option[universe.v4.model.PackageDefinition] = {
      getLabel(MarathonApp.packageLabel).map { string =>
        decode64[StorageEnvelope](
          string
        ).decodeData[universe.v4.model.PackageDefinition].getOrThrow
      }
    }

    def serviceOptions: Option[JsonObject] = {
      getLabel(MarathonApp.optionsLabel).flatMap { string =>
        parse64(string).asObject
      }
    }

  }
}
