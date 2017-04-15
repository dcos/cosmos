package com.mesosphere.cosmos.bijection

import com.mesosphere.cosmos.label
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod

object CosmosConversions {

  implicit val v2PackageToLabelV1PackageMetadata
  : Conversion[universe.v3.model.V2Package, label.v1.model.PackageMetadata] = {
    Conversion.fromFunction { pkg: universe.v3.model.V2Package =>
      label.v1.model.PackageMetadata(
        pkg.packagingVersion.as[universe.v2.model.PackagingVersion],
        pkg.name,
        pkg.version.as[universe.v2.model.PackageDetailsVersion],
        pkg.maintainer,
        pkg.description,
        pkg.tags.as[List[String]],
        pkg.selected orElse Some(false),
        pkg.scm,
        pkg.website,
        pkg.framework orElse Some(false),
        pkg.preInstallNotes,
        pkg.postInstallNotes,
        pkg.postUninstallNotes,
        pkg.licenses.map(_.as[List[universe.v2.model.License]]),
        pkg.resource.flatMap(_.images.map(_.as[universe.v2.model.Images]))
      )
    }
  }

  implicit val v3PackageToLabelV1PackageMetadata
  : Conversion[universe.v3.model.V3Package, label.v1.model.PackageMetadata] = {
    Conversion.fromFunction { pkg: universe.v3.model.V3Package =>
      label.v1.model.PackageMetadata(
        pkg.packagingVersion.as[universe.v2.model.PackagingVersion],
        pkg.name,
        pkg.version.as[universe.v2.model.PackageDetailsVersion],
        pkg.maintainer,
        pkg.description,
        pkg.tags.as[List[String]],
        pkg.selected orElse Some(false),
        pkg.scm,
        pkg.website,
        pkg.framework orElse Some(false),
        pkg.preInstallNotes,
        pkg.postInstallNotes,
        pkg.postUninstallNotes,
        pkg.licenses.map(_.as[List[universe.v2.model.License]]),
        pkg.resource.flatMap(_.images.map(_.as[universe.v2.model.Images]))
      )
    }
  }

  implicit val v4PackageToLabelV1PackageMetadata
  : Conversion[universe.v4.model.V4Package, label.v1.model.PackageMetadata] = {
    Conversion.fromFunction { pkg: universe.v4.model.V4Package =>
      label.v1.model.PackageMetadata(
        pkg.packagingVersion.as[universe.v2.model.PackagingVersion],
        pkg.name,
        pkg.version.as[universe.v2.model.PackageDetailsVersion],
        pkg.maintainer,
        pkg.description,
        pkg.tags.as[List[String]],
        pkg.selected orElse Some(false),
        pkg.scm,
        pkg.website,
        pkg.framework orElse Some(false),
        pkg.preInstallNotes,
        pkg.postInstallNotes,
        pkg.postUninstallNotes,
        pkg.licenses.map(_.as[List[universe.v2.model.License]]),
        pkg.resource.flatMap(_.images.map(_.as[universe.v2.model.Images]))
      )
    }
  }

  implicit val packageDefinitionToLabelV1PackageMetadata
  : Conversion[universe.v4.model.PackageDefinition, label.v1.model.PackageMetadata] = {
    Conversion.fromFunction {
      case pkg: universe.v3.model.V2Package => v2PackageToLabelV1PackageMetadata(pkg)
      case pkg: universe.v3.model.V3Package => v3PackageToLabelV1PackageMetadata(pkg)
      case pkg: universe.v4.model.V4Package => v4PackageToLabelV1PackageMetadata(pkg)
    }
  }

}
