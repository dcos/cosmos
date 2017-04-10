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

  implicit val supportedPackageToMetadata:
    Conversion[universe.v3.model.SupportedPackageDefinition,
      (universe.v3.model.Metadata, universe.v3.model.ReleaseVersion)] = {
    Conversion.fromFunction {
      case v3Package: universe.v3.model.V3Package =>
        v3Package.as[(universe.v3.model.V3Metadata, universe.v3.model.ReleaseVersion)]
    }
  }

}
