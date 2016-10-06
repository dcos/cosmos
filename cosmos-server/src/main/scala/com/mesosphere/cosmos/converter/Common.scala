package com.mesosphere.cosmos.converter

import com.mesosphere.universe
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.Bijection

object Common {

  implicit val V2BundleToV2Package: Bijection[
    (universe.v3.model.V2Bundle, universe.v3.model.PackageDefinition.ReleaseVersion),
    universe.v3.model.V2Package // TODO Move these "Bundle" objects out of v3 they don't belong there
    ] = {
    def fwd(bundlePair: (universe.v3.model.V2Bundle, universe.v3.model.PackageDefinition.ReleaseVersion)): universe.v3.model.V2Package = {
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

    def rev(v2: universe.v3.model.V2Package): (universe.v3.model.V2Bundle, universe.v3.model.PackageDefinition.ReleaseVersion) =
      (universe.v3.model.V2Bundle(
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
    (universe.v3.model.V3Bundle, universe.v3.model.PackageDefinition.ReleaseVersion),
    universe.v3.model.V3Package
    ] = {
    def fwd(bundlePair: (universe.v3.model.V3Bundle, universe.v3.model.PackageDefinition.ReleaseVersion)): universe.v3.model.V3Package = {
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

    def rev(v3: universe.v3.model.V3Package): (universe.v3.model.V3Bundle, universe.v3.model.PackageDefinition.ReleaseVersion) =
      (universe.v3.model.V3Bundle(
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
    (universe.v3.model.BundleDefinition, universe.v3.model.PackageDefinition.ReleaseVersion),
    universe.v3.model.PackageDefinition
    ] = {
    def fwd(bundlePair: (universe.v3.model.BundleDefinition, universe.v3.model.PackageDefinition.ReleaseVersion)): universe.v3.model.PackageDefinition = {
      val (bundle, releaseVersion) = bundlePair
      bundle match {
        case v2: universe.v3.model.V2Bundle => (v2, releaseVersion).as[universe.v3.model.V2Package]
        case v3: universe.v3.model.V3Bundle => (v3, releaseVersion).as[universe.v3.model.V3Package]
      }
    }

    def rev(packageDefinition: universe.v3.model.PackageDefinition): (universe.v3.model.BundleDefinition, universe.v3.model.PackageDefinition.ReleaseVersion) = {
      packageDefinition match {
        case v2: universe.v3.model.V2Package => v2.as[(universe.v3.model.V2Bundle, universe.v3.model.PackageDefinition.ReleaseVersion)]
        case v3: universe.v3.model.V3Package => v3.as[(universe.v3.model.V3Bundle, universe.v3.model.PackageDefinition.ReleaseVersion)]
      }
    }

    Bijection.build(fwd)(rev)
  }


}
