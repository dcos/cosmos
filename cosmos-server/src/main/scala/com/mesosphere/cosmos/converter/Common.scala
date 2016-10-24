package com.mesosphere.cosmos.converter

import cats.data.Xor
import com.mesosphere.cosmos._
import com.mesosphere.cosmos.internal.model
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.universe
import com.twitter.bijection.Bijection
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.Injection
import io.circe.jawn.decode
import io.circe.syntax._
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object Common {

  implicit val V2BundleToV2Package: Bijection[
    (model.V2Bundle, universe.v3.model.PackageDefinition.ReleaseVersion),
    universe.v3.model.V2Package // TODO Move these "Bundle" objects out of v3 they don't belong there
    ] = {
    def fwd(bundlePair: (model.V2Bundle, universe.v3.model.PackageDefinition.ReleaseVersion)): universe.v3.model.V2Package = {
      val v2 = bundlePair._1
      val releaseVersion = bundlePair._2
      universe.v3.model.V2Package(
        v2.packagingVersion,
        v2.name,
        v2.version,
        releaseVersion,
        v2.maintainer,
        v2.description,
        v2.marathon,
        v2.tags,
        v2.selected,
        v2.scm,
        v2.website,
        v2.framework,
        v2.preInstallNotes,
        v2.postInstallNotes,
        v2.postUninstallNotes,
        v2.licenses,
        v2.resource,
        v2.config,
        v2.command
      )
    }

    def rev(v2: universe.v3.model.V2Package): (model.V2Bundle, universe.v3.model.PackageDefinition.ReleaseVersion) =
      (model.V2Bundle(
        v2.packagingVersion,
        v2.name,
        v2.version,
        v2.maintainer,
        v2.description,
        v2.marathon,
        v2.tags,
        v2.selected,
        v2.scm,
        v2.website,
        v2.framework,
        v2.preInstallNotes,
        v2.postInstallNotes,
        v2.postUninstallNotes,
        v2.licenses,
        v2.resource,
        v2.config,
        v2.command
      ),
        v2.releaseVersion)

    Bijection.build(fwd)(rev)
  }

  implicit val V3BundleToV3Package: Bijection[
    (model.V3Bundle, universe.v3.model.PackageDefinition.ReleaseVersion),
    universe.v3.model.V3Package
    ] = {
    def fwd(bundlePair: (model.V3Bundle, universe.v3.model.PackageDefinition.ReleaseVersion)): universe.v3.model.V3Package = {
      val v3 = bundlePair._1
      val releaseVersion = bundlePair._2
      universe.v3.model.V3Package(
        v3.packagingVersion,
        v3.name,
        v3.version,
        releaseVersion,
        v3.maintainer,
        v3.description,
        v3.tags,
        v3.selected,
        v3.scm,
        v3.website,
        v3.framework,
        v3.preInstallNotes,
        v3.postInstallNotes,
        v3.postUninstallNotes,
        v3.licenses,
        v3.minDcosReleaseVersion,
        v3.marathon,
        v3.resource,
        v3.config,
        v3.command
      )
    }

    def rev(v3: universe.v3.model.V3Package): (model.V3Bundle, universe.v3.model.PackageDefinition.ReleaseVersion) =
      (model.V3Bundle(
        v3.packagingVersion,
        v3.name,
        v3.version,
        v3.maintainer,
        v3.description,
        v3.tags,
        v3.selected,
        v3.scm,
        v3.website,
        v3.framework,
        v3.preInstallNotes,
        v3.postInstallNotes,
        v3.postUninstallNotes,
        v3.licenses,
        v3.minDcosReleaseVersion,
        v3.marathon,
        v3.resource,
        v3.config,
        v3.command
      ),
        v3.releaseVersion)

    Bijection.build(fwd)(rev)
  }

  implicit val BundleToPackage: Bijection[
    (model.BundleDefinition, universe.v3.model.PackageDefinition.ReleaseVersion),
    universe.v3.model.PackageDefinition
    ] = {
    def fwd(bundlePair: (model.BundleDefinition, universe.v3.model.PackageDefinition.ReleaseVersion)): universe.v3.model.PackageDefinition = {
      val (bundle, releaseVersion) = bundlePair
      bundle match {
        case v2: model.V2Bundle => (v2, releaseVersion).as[universe.v3.model.V2Package]
        case v3: model.V3Bundle => (v3, releaseVersion).as[universe.v3.model.V3Package]
      }
    }

    def rev(packageDefinition: universe.v3.model.PackageDefinition): (model.BundleDefinition, universe.v3.model.PackageDefinition.ReleaseVersion) = {
      packageDefinition match {
        case v2: universe.v3.model.V2Package => v2.as[(model.V2Bundle, universe.v3.model.PackageDefinition.ReleaseVersion)]
        case v3: universe.v3.model.V3Package => v3.as[(model.V3Bundle, universe.v3.model.PackageDefinition.ReleaseVersion)]
      }
    }

    Bijection.build(fwd)(rev)
  }

  implicit val packageCoordinateToBase64String: Injection[PackageCoordinate, String] = {
    def fwd(coordinate: PackageCoordinate): String = {
      Base64.getUrlEncoder.encodeToString(
        coordinate.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
      )
    }

    def rev(str: String): Try[PackageCoordinate] = {
      Try {
        val coordinate  = new String(
          Base64.getUrlDecoder.decode(str),
          StandardCharsets.UTF_8
        )

        decode[PackageCoordinate](coordinate) match {
          case Xor.Left(err) => throw CirceError(err)
          case Xor.Right(c) => c
        }
      }
    }

    Injection.build[PackageCoordinate, String](fwd)(rev)
  }

  implicit val versionToString = Injection.build[universe.v3.model.SemVer, String] { version =>
    version.toString
  } { string =>
    universe.v3.model.SemVer(string).map(Success(_)).getOrElse(
      Failure(new IllegalArgumentException(s"Unable to parse $string as semver"))
    )
  }
}
