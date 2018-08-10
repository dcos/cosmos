package com.mesosphere.universe.bijection

import com.mesosphere.universe
import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod

object TestUniverseConversions {

  implicit val v3PackageToV3Metadata:
    Conversion[universe.v3.model.V3Package,
      (universe.v3.model.V3Metadata, universe.v3.model.ReleaseVersion)] = {
    Conversion.fromFunction { v3Package =>
      val metadata = universe.v3.model.V3Metadata(
        packagingVersion = v3Package.packagingVersion,
        name = v3Package.name,
        version = v3Package.version,
        maintainer = v3Package.maintainer,
        description = v3Package.description,
        tags = v3Package.tags,
        scm = v3Package.scm,
        website = v3Package.website,
        framework = v3Package.framework,
        preInstallNotes = v3Package.preInstallNotes,
        postInstallNotes = v3Package.postInstallNotes,
        postUninstallNotes = v3Package.postUninstallNotes,
        licenses = v3Package.licenses,
        minDcosReleaseVersion = v3Package.minDcosReleaseVersion,
        marathon = v3Package.marathon,
        resource = v3Package.resource,
        config = v3Package.config
      )

      (metadata, v3Package.releaseVersion)
    }
  }

  private implicit val v4PackageToV4Metadata:
    Conversion[universe.v4.model.V4Package,
      (universe.v4.model.V4Metadata, universe.v3.model.ReleaseVersion)] = {
    Conversion.fromFunction { v4Package =>
      val metadata = universe.v4.model.V4Metadata(
        packagingVersion = v4Package.packagingVersion,
        name = v4Package.name,
        version = v4Package.version,
        maintainer = v4Package.maintainer,
        description = v4Package.description,
        tags = v4Package.tags,
        scm = v4Package.scm,
        website = v4Package.website,
        framework = v4Package.framework,
        preInstallNotes = v4Package.preInstallNotes,
        postInstallNotes = v4Package.postInstallNotes,
        postUninstallNotes = v4Package.postUninstallNotes,
        licenses = v4Package.licenses,
        minDcosReleaseVersion = v4Package.minDcosReleaseVersion,
        marathon = v4Package.marathon,
        resource = v4Package.resource,
        config = v4Package.config,
        upgradesFrom = v4Package.upgradesFrom,
        downgradesTo = v4Package.downgradesTo
      )

      (metadata, v4Package.releaseVersion)
    }
  }

  private implicit val v5PackageToV5Metadata:
    Conversion[universe.v5.model.V5Package,
      (universe.v5.model.V5Metadata, universe.v3.model.ReleaseVersion)] = {
    Conversion.fromFunction { v5Package =>
      val metadata = universe.v5.model.V5Metadata(
        packagingVersion = v5Package.packagingVersion,
        name = v5Package.name,
        version = v5Package.version,
        maintainer = v5Package.maintainer,
        description = v5Package.description,
        tags = v5Package.tags,
        scm = v5Package.scm,
        website = v5Package.website,
        framework = v5Package.framework,
        preInstallNotes = v5Package.preInstallNotes,
        postInstallNotes = v5Package.postInstallNotes,
        postUninstallNotes = v5Package.postUninstallNotes,
        licenses = v5Package.licenses,
        minDcosReleaseVersion = v5Package.minDcosReleaseVersion,
        marathon = v5Package.marathon,
        resource = v5Package.resource,
        config = v5Package.config,
        upgradesFrom = v5Package.upgradesFrom,
        downgradesTo = v5Package.downgradesTo,
        manager =  v5Package.manager
      )

      (metadata, v5Package.releaseVersion)
    }
  }

  implicit val supportedPackageToMetadata:
    Conversion[universe.v4.model.SupportedPackageDefinition,
      (universe.v4.model.Metadata, universe.v3.model.ReleaseVersion)] = {
    Conversion.fromFunction {
      case v3Package: universe.v3.model.V3Package =>
        v3Package.as[(universe.v3.model.V3Metadata, universe.v3.model.ReleaseVersion)]
      case v4Package: universe.v4.model.V4Package =>
        v4Package.as[(universe.v4.model.V4Metadata, universe.v3.model.ReleaseVersion)]
      case v5Package: universe.v5.model.V5Package =>
        v5Package.as[(universe.v5.model.V5Metadata, universe.v3.model.ReleaseVersion)]
    }
  }

}
