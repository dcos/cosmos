package com.mesosphere.universe

import cats.data.NonEmptyList
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.finch.MediaTypedDecoder
import com.mesosphere.cosmos.finch.MediaTypedEncoder
import com.mesosphere.http.MediaType
import com.mesosphere.universe
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.HCursor
import io.circe.Json
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps

package v3.model {

  /**
    * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/v20Package
    */
  case class V2Package(
    packagingVersion: V2PackagingVersion.type = V2PackagingVersion,
    name: String,
    version: Version,
    releaseVersion: ReleaseVersion,
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
  ) extends universe.v4.model.PackageDefinition with Ordered[V2Package] {
    override def compare(that: V2Package): Int = {
      universe.v4.model.PackageDefinition.compare(
        (name, version, releaseVersion),
        (that.name, that.version, that.releaseVersion)
      )
    }
  }

  object V2Package {
    implicit val decoder: Decoder[V2Package] = deriveDecoder[V2Package]
    implicit val encoder: Encoder[V2Package] = deriveEncoder[V2Package]

    implicit val mediaTypedEncoder: MediaTypedEncoder[V2Package] = MediaTypedEncoder(
      MediaTypes.universeV2Package
    )
  }

  /**
    * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/v30Package
    */
  case class V3Package(
    packagingVersion: V3PackagingVersion.type = V3PackagingVersion,
    name: String,
    version: Version,
    releaseVersion: ReleaseVersion,
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
  ) extends universe.v4.model.SupportedPackageDefinition with Ordered[V3Package] {
    override def compare(that: V3Package): Int = {
      universe.v4.model.PackageDefinition.compare(
        (name, version, releaseVersion),
        (that.name, that.version, that.releaseVersion)
      )
    }
  }

  object V3Package {
    implicit val decoder: Decoder[V3Package] = deriveDecoder[V3Package]
    implicit val encoder: Encoder[V3Package] = deriveEncoder[V3Package]

    implicit val mediaTypedEncoder: MediaTypedEncoder[V3Package] = MediaTypedEncoder(
      MediaTypes.universeV3Package
    )
  }

}

package v4.model {

  sealed trait PackageDefinition

  object PackageDefinition {

    implicit val packageDefinitionOrdering = new Ordering[PackageDefinition] {
      override def compare(a: PackageDefinition, b: PackageDefinition): Int = {
        PackageDefinition.compare(
          (a.name, a.version, a.releaseVersion),
          (b.name, b.version, b.releaseVersion)
        )
      }
    }

    def compare(
      a: (String, universe.v3.model.Version, universe.v3.model.ReleaseVersion),
      b: (String, universe.v3.model.Version, universe.v3.model.ReleaseVersion)
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

    implicit val decoder: Decoder[PackageDefinition] = {
      Decoder.instance[PackageDefinition] { (hc: HCursor) =>
        hc.downField("packagingVersion").as[universe.v4.model.PackagingVersion].flatMap {
          case universe.v3.model.V2PackagingVersion => hc.as[universe.v3.model.V2Package]
          case universe.v3.model.V3PackagingVersion => hc.as[universe.v3.model.V3Package]
          case universe.v4.model.V4PackagingVersion => hc.as[universe.v4.model.V4Package]
          case universe.v5.model.V5PackagingVersion => hc.as[universe.v5.model.V5Package]
        }
      }
    }

    implicit val encoder: Encoder[PackageDefinition] = Encoder.instance {
      case v2: universe.v3.model.V2Package => v2.asJson
      case v3: universe.v3.model.V3Package => v3.asJson
      case v4: universe.v4.model.V4Package => v4.asJson
      case v5: universe.v5.model.V5Package => v5.asJson
    }

    private val mediaTypes = (
      universe.v3.model.V2Package.mediaTypedEncoder.mediaTypes ++
      universe.v3.model.V3Package.mediaTypedEncoder.mediaTypes.toList ++
      universe.v4.model.V4Package.mediaTypedEncoder.mediaTypes.toList ++
      universe.v5.model.V5Package.mediaTypedEncoder.mediaTypes.toList
    )

    implicit val mediaTypedDecoder: MediaTypedDecoder[PackageDefinition] = MediaTypedDecoder(
      mediaTypes
    )

    implicit val mediaTypedEncoder: MediaTypedEncoder[PackageDefinition] = {
      new MediaTypedEncoder[PackageDefinition] {
        val encoder = PackageDefinition.encoder

        val mediaTypes: NonEmptyList[MediaType] = PackageDefinition.mediaTypes

        def mediaType(a: PackageDefinition): MediaType = a match {
          case v2: universe.v3.model.V2Package =>
            universe.v3.model.V2Package.mediaTypedEncoder.mediaType(v2)
          case v3: universe.v3.model.V3Package =>
            universe.v3.model.V3Package.mediaTypedEncoder.mediaType(v3)
          case v4: universe.v4.model.V4Package =>
            universe.v4.model.V4Package.mediaTypedEncoder.mediaType(v4)
          case v5: universe.v5.model.V5Package =>
            universe.v5.model.V5Package.mediaTypedEncoder.mediaType(v5)
        }
      }
    }

    // scalastyle:off number.of.methods
    implicit final class PackageDefinitionOps(val pkgDef: universe.v4.model.PackageDefinition) extends AnyVal {

      def packageCoordinate: universe.v1.model.PackageCoordinate =  {
        universe.v1.model.PackageCoordinate(name, version)
      }

      def packagingVersion: universe.v4.model.PackagingVersion = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.packagingVersion
        case v3: universe.v3.model.V3Package => v3.packagingVersion
        case v4: universe.v4.model.V4Package => v4.packagingVersion
        case v5: universe.v5.model.V5Package => v5.packagingVersion
      }

      def name: String = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.name
        case v3: universe.v3.model.V3Package => v3.name
        case v4: universe.v4.model.V4Package => v4.name
        case v5: universe.v5.model.V5Package => v5.name
      }

      def version: universe.v3.model.Version = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.version
        case v3: universe.v3.model.V3Package => v3.version
        case v4: universe.v4.model.V4Package => v4.version
        case v5: universe.v5.model.V5Package => v5.version
      }

      //noinspection MutatorLikeMethodIsParameterless
      def releaseVersion: universe.v3.model.ReleaseVersion = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.releaseVersion
        case v3: universe.v3.model.V3Package => v3.releaseVersion
        case v4: universe.v4.model.V4Package => v4.releaseVersion
        case v5: universe.v5.model.V5Package => v5.releaseVersion
      }

      def maintainer: String = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.maintainer
        case v3: universe.v3.model.V3Package => v3.maintainer
        case v4: universe.v4.model.V4Package => v4.maintainer
        case v5: universe.v5.model.V5Package => v5.maintainer
      }

      def description: String = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.description
        case v3: universe.v3.model.V3Package => v3.description
        case v4: universe.v4.model.V4Package => v4.description
        case v5: universe.v5.model.V5Package => v5.maintainer
      }

      def marathon: Option[universe.v3.model.Marathon] = pkgDef match {
        case v2: universe.v3.model.V2Package => Some(v2.marathon)
        case v3: universe.v3.model.V3Package => v3.marathon
        case v4: universe.v4.model.V4Package => v4.marathon
        case v5: universe.v5.model.V5Package => v5.marathon
      }

      def tags: List[universe.v3.model.Tag] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.tags
        case v3: universe.v3.model.V3Package => v3.tags
        case v4: universe.v4.model.V4Package => v4.tags
        case v5: universe.v5.model.V5Package => v5.tags
      }

      def selected: Option[Boolean] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.selected
        case v3: universe.v3.model.V3Package => v3.selected
        case v4: universe.v4.model.V4Package => v4.selected
        case v5: universe.v5.model.V5Package => v5.selected
      }

      def scm: Option[String]= pkgDef match {
        case v2: universe.v3.model.V2Package => v2.scm
        case v3: universe.v3.model.V3Package => v3.scm
        case v4: universe.v4.model.V4Package => v4.scm
        case v5: universe.v5.model.V5Package => v5.scm
      }

      def website: Option[String] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.website
        case v3: universe.v3.model.V3Package => v3.website
        case v4: universe.v4.model.V4Package => v4.website
        case v5: universe.v5.model.V5Package => v5.website
      }

      def framework: Option[Boolean] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.framework
        case v3: universe.v3.model.V3Package => v3.framework
        case v4: universe.v4.model.V4Package => v4.framework
        case v5: universe.v5.model.V5Package => v5.framework
      }

      def preInstallNotes: Option[String] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.preInstallNotes
        case v3: universe.v3.model.V3Package => v3.preInstallNotes
        case v4: universe.v4.model.V4Package => v4.preInstallNotes
        case v5: universe.v5.model.V5Package => v5.preInstallNotes
      }

      def postInstallNotes: Option[String] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.postInstallNotes
        case v3: universe.v3.model.V3Package => v3.postInstallNotes
        case v4: universe.v4.model.V4Package => v4.postInstallNotes
        case v5: universe.v5.model.V5Package => v5.postInstallNotes
      }

      def postUninstallNotes: Option[String] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.postUninstallNotes
        case v3: universe.v3.model.V3Package => v3.postUninstallNotes
        case v4: universe.v4.model.V4Package => v4.postUninstallNotes
        case v5: universe.v5.model.V5Package => v5.postUninstallNotes
      }

      def licenses: Option[List[universe.v3.model.License]] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.licenses
        case v3: universe.v3.model.V3Package => v3.licenses
        case v4: universe.v4.model.V4Package => v4.licenses
        case v5: universe.v5.model.V5Package => v5.licenses
      }

      def config: Option[JsonObject] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.config
        case v3: universe.v3.model.V3Package => v3.config
        case v4: universe.v4.model.V4Package => v4.config
        case v5: universe.v5.model.V5Package => v5.config
      }

      def command: Option[universe.v3.model.Command] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.command
        case v3: universe.v3.model.V3Package => v3.command
        case _ => None // command is removed v4 and above
      }

      def minDcosReleaseVersion: Option[universe.v3.model.DcosReleaseVersion] = pkgDef match {
        case _: universe.v3.model.V2Package => None
        case v3: universe.v3.model.V3Package => v3.minDcosReleaseVersion
        case v4: universe.v4.model.V4Package => v4.minDcosReleaseVersion
        case v5: universe.v5.model.V5Package => v5.minDcosReleaseVersion
      }

      def v3Resource: Option[universe.v3.model.V3Resource] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.resource.map {
          case universe.v3.model.V2Resource(assets, images) =>
            universe.v3.model.V3Resource(assets, images)
        }
          case v3: universe.v3.model.V3Package => v3.resource
          case v4: universe.v4.model.V4Package => v4.resource
          case v5: universe.v5.model.V5Package => v5.resource
      }

      def downgradesTo: List[universe.v3.model.VersionSpecification] = pkgDef match {
        case v4: universe.v4.model.V4Package => v4.downgradesTo.getOrElse(Nil)
        case v5: universe.v5.model.V5Package => v5.downgradesTo.getOrElse(Nil)
        case _ => Nil
      }

      def manager: Option[universe.v5.model.Manager] = pkgDef match {
        case v5: universe.v5.model.V5Package => v5.manager
        case _ => None
      }

      def upgradesFrom: List[universe.v3.model.VersionSpecification] = pkgDef match {
        case v4: universe.v4.model.V4Package => v4.upgradesFrom.getOrElse(Nil)
        case v5: universe.v5.model.V5Package => v5.upgradesFrom.getOrElse(Nil)
        case _ => Nil
      }

      def canUpgradeFrom(version: universe.v3.model.Version): Boolean = {
        upgradesFrom.exists(_.matches(version))
      }

      def canDowngradeTo(version: universe.v3.model.Version): Boolean = {
        downgradesTo.exists(_.matches(version))
      }

      // -------- Non top-level properties that we are safe to "jump" to --------------

      def images: Option[universe.v3.model.Images] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.resource.flatMap(_.images)
        case v3: universe.v3.model.V3Package => v3.resource.flatMap(_.images)
        case v4: universe.v4.model.V4Package => v4.resource.flatMap(_.images)
        case v5: universe.v5.model.V5Package => v5.resource.flatMap(_.images)
      }

      def cli: Option[universe.v3.model.Cli] = pkgDef match {
        case _ : universe.v3.model.V2Package => None
        case v3: universe.v3.model.V3Package => v3.resource.flatMap(_.cli)
        case v4: universe.v4.model.V4Package => v4.resource.flatMap(_.cli)
        case v5: universe.v5.model.V5Package => v5.resource.flatMap(_.cli)
      }

      def assets: Option[universe.v3.model.Assets] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.resource.flatMap(_.assets)
        case v3: universe.v3.model.V3Package => v3.resource.flatMap(_.assets)
        case v4: universe.v4.model.V4Package => v4.resource.flatMap(_.assets)
        case v5: universe.v5.model.V5Package => v5.resource.flatMap(_.assets)
      }

      // -------- Utility methods to rewrite the resource urls for proxy endpoint ------
      def rewrite(
        urlRewrite: (String) => String,
        dockerIdRewrite: (String) => String
      ): universe.v4.model.PackageDefinition = {
        PackageDefinition.rewrite(pkgDef, urlRewrite, dockerIdRewrite)
      }

      // -------- Utility Methods to convert to Json -----------------------------------
      def resourceJson: Option[Json] = pkgDef match {
        case v2: universe.v3.model.V2Package => v2.resource.map(_.asJson)
        case v3: universe.v3.model.V3Package => v3.resource.map(_.asJson)
        case v4: universe.v4.model.V4Package => v4.resource.map(_.asJson)
        case v5: universe.v5.model.V5Package => v5.resource.map(_.asJson)
      }

    }

    // scalastyle:off cyclomatic.complexity
    // scalastyle:off method.length
    def rewrite[T <: PackageDefinition](
      pkg: T,
      urlRewrite: (String) => String,
      dockerIdRewrite: (String) => String
    ): T = {
      // TODO We should not have to do `.asInstanceOf[T]` find if this is a scala bug and report/fix.
      pkg match {
        case v2: universe.v3.model.V2Package => v2.resource match {
          case Some(r) => v2.copy(
            resource = Some(
              universe.v3.model.V2Resource(
                r.assets.map(rewriteAssets(urlRewrite, dockerIdRewrite)),
                r.images.map(rewriteImages(urlRewrite))
              )
            )
          ).asInstanceOf[T]
          case None => pkg
        }
        case v3: universe.v3.model.V3Package => v3.resource match {
          case Some(r) => v3.copy(
            resource = Some(
              universe.v3.model.V3Resource(
                r.assets.map(rewriteAssets(urlRewrite, dockerIdRewrite)),
                r.images.map(rewriteImages(urlRewrite)),
                r.cli.map(rewriteCli(urlRewrite))
              )
            )
          ).asInstanceOf[T]
          case None => pkg
        }
        case v4: universe.v4.model.V4Package => v4.resource match {
          case Some(r) => v4.copy(
            resource = Some(
              universe.v3.model.V3Resource(
                r.assets.map(rewriteAssets(urlRewrite, dockerIdRewrite)),
                r.images.map(rewriteImages(urlRewrite)),
                r.cli.map(rewriteCli(urlRewrite))
              )
            )
          ).asInstanceOf[T]
          case None => pkg
        }
        case v5: universe.v5.model.V5Package => v5.resource match {
          case Some(r) => v5.copy(
            resource = Some(
              universe.v3.model.V3Resource(
                r.assets.map(rewriteAssets(urlRewrite, dockerIdRewrite)),
                r.images.map(rewriteImages(urlRewrite)),
                r.cli.map(rewriteCli(urlRewrite))
              )
            )
          ).asInstanceOf[T]
          case None => pkg
        }
      }
    }
    // scalastyle:on method.length
    // scalastyle:on cyclomatic.complexity
  }
  // scalastyle:on number.of.methods

  sealed trait SupportedPackageDefinition
    extends universe.v4.model.PackageDefinition

  object SupportedPackageDefinition {

    implicit val ordering: Ordering[SupportedPackageDefinition] =
      universe.v4.model.PackageDefinition.packageDefinitionOrdering.on(identity)

    implicit val decoder: Decoder[SupportedPackageDefinition] = {
      Decoder.instance[SupportedPackageDefinition] { (hc: HCursor) =>
        hc.downField("packagingVersion").as[universe.v4.model.PackagingVersion].flatMap {
          case universe.v5.model.V5PackagingVersion => hc.as[universe.v5.model.V5Package]
          case universe.v4.model.V4PackagingVersion => hc.as[universe.v4.model.V4Package]
          case universe.v3.model.V3PackagingVersion => hc.as[universe.v3.model.V3Package]
          case universe.v3.model.V2PackagingVersion =>
            Left(
              DecodingFailure(
                s"V2Package is not a supported package definition",
                hc.history
              )
            )
        }
      }
    }

    implicit val encoder: Encoder[SupportedPackageDefinition] = {
      Encoder.instance {
        case v3: universe.v3.model.V3Package => v3.asJson
        case v4: universe.v4.model.V4Package => v4.asJson
        case v5: universe.v5.model.V5Package => v5.asJson
      }
    }

  }

  /**
    * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/v40Package
    */
  case class V4Package(
    packagingVersion: V4PackagingVersion.type = V4PackagingVersion,
    name: String,
    version: universe.v3.model.Version,
    releaseVersion: universe.v3.model.ReleaseVersion,
    maintainer: String,
    description: String,
    tags: List[universe.v3.model.Tag] = Nil,
    selected: Option[Boolean] = None,
    scm: Option[String] = None,
    website: Option[String] = None,
    framework: Option[Boolean] = None,
    preInstallNotes: Option[String] = None,
    postInstallNotes: Option[String] = None,
    postUninstallNotes: Option[String] = None,
    licenses: Option[List[universe.v3.model.License]] = None,
    minDcosReleaseVersion: Option[universe.v3.model.DcosReleaseVersion] = None,
    marathon: Option[universe.v3.model.Marathon] = None,
    resource: Option[universe.v3.model.V3Resource] = None,
    config: Option[JsonObject] = None,
    upgradesFrom: Option[List[universe.v3.model.VersionSpecification]] = None,
    downgradesTo: Option[List[universe.v3.model.VersionSpecification]] = None
  ) extends universe.v4.model.SupportedPackageDefinition

  object V4Package {
    implicit val decoder: Decoder[V4Package] = {
      deriveDecoder[V4Package]
    }
    implicit val encoder: Encoder[V4Package] = {
      deriveEncoder[V4Package]
    }

    implicit val mediaTypedEncoder: MediaTypedEncoder[V4Package] = MediaTypedEncoder(
      MediaTypes.universeV4Package
    )
  }
}

package v5.model {

  case class V5Package(
      packagingVersion: V5PackagingVersion.type = V5PackagingVersion,
      name: String,
      version: universe.v3.model.Version,
      releaseVersion: universe.v3.model.ReleaseVersion,
      maintainer: String,
      description: String,
      tags: List[universe.v3.model.Tag] = Nil,
      selected: Option[Boolean] = None,
      scm: Option[String] = None,
      website: Option[String] = None,
      framework: Option[Boolean] = None,
      preInstallNotes: Option[String] = None,
      postInstallNotes: Option[String] = None,
      postUninstallNotes: Option[String] = None,
      licenses: Option[List[universe.v3.model.License]] = None,
      minDcosReleaseVersion: Option[universe.v3.model.DcosReleaseVersion] = None,
      marathon: Option[universe.v3.model.Marathon] = None,
      resource: Option[universe.v3.model.V3Resource] = None,
      config: Option[JsonObject] = None,
      upgradesFrom: Option[List[universe.v3.model.VersionSpecification]] = None,
      downgradesTo: Option[List[universe.v3.model.VersionSpecification]] = None,
      manager: Option[universe.v5.model.Manager] = None
    ) extends universe.v4.model.SupportedPackageDefinition

  object V5Package {
    implicit val decoder: Decoder[V5Package] = {
      deriveDecoder[V5Package]
    }
    implicit val encoder: Encoder[V5Package] = {
      deriveEncoder[V5Package]
    }

    implicit val mediaTypedEncoder: MediaTypedEncoder[V5Package] = MediaTypedEncoder(
      MediaTypes.universeV5Package
    )
  }
}
