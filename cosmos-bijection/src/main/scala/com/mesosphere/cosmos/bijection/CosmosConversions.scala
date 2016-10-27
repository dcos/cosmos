package com.mesosphere.cosmos.bijection

import com.mesosphere.cosmos.label.v1.model.PackageMetadata
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.v2
import com.mesosphere.universe.v3.model.{PackageDefinition, V2Package, V3Package}
import com.twitter.bijection.Conversion
import com.twitter.bijection.Conversion.asMethod

object CosmosConversions {

  implicit val v2PackageToLabelV1PackageMetadata: Conversion[V2Package, PackageMetadata] = {
    Conversion.fromFunction { pkg: V2Package =>
      PackageMetadata(
        pkg.packagingVersion.as[v2.model.PackagingVersion],
        pkg.name,
        pkg.version.as[v2.model.PackageDetailsVersion],
        pkg.maintainer,
        pkg.description,
        pkg.tags.as[List[String]],
        pkg.selected,
        pkg.scm,
        pkg.website,
        pkg.framework,
        pkg.preInstallNotes,
        pkg.postInstallNotes,
        pkg.postUninstallNotes,
        pkg.licenses.map(_.as[List[v2.model.License]]),
        pkg.resource.flatMap(_.images.map(_.as[v2.model.Images]))
      )
    }
  }

  implicit val v3PackageToLabelV1PackageMetadata: Conversion[V3Package, PackageMetadata] = {
    Conversion.fromFunction { pkg: V3Package =>
      PackageMetadata(
        pkg.packagingVersion.as[v2.model.PackagingVersion],
        pkg.name,
        pkg.version.as[v2.model.PackageDetailsVersion],
        pkg.maintainer,
        pkg.description,
        pkg.tags.as[List[String]],
        pkg.selected,
        pkg.scm,
        pkg.website,
        pkg.framework,
        pkg.preInstallNotes,
        pkg.postInstallNotes,
        pkg.postUninstallNotes,
        pkg.licenses.map(_.as[List[v2.model.License]]),
        pkg.resource.flatMap(_.images.map(_.as[v2.model.Images]))
      )
    }
  }

  implicit val packageDefinitionToLabelV1PackageMetadata:
    Conversion[PackageDefinition, PackageMetadata] = {
    Conversion.fromFunction {
      case pkg: V2Package => v2PackageToLabelV1PackageMetadata(pkg)
      case pkg: V3Package => v3PackageToLabelV1PackageMetadata(pkg)
    }
  }

}
