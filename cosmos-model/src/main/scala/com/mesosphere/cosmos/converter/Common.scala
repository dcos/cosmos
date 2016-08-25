package com.mesosphere.cosmos.converter

import com.mesosphere.universe
import com.mesosphere.universe.v3.model.PackageDefinition.ReleaseVersion
import com.mesosphere.universe.v3.model._
import com.netaporter.uri.Uri
import com.twitter.bijection.{Bijection, Injection}
import com.twitter.bijection.Conversion.asMethod

import scala.util.{Failure, Success, Try}

object Common {

  implicit val V2BundleToV2Package
  : Bijection[(V2Bundle, ReleaseVersion), V2Package] = {
    def fwd(bundlePair: (V2Bundle, ReleaseVersion)): V2Package = {
      val v2 = bundlePair._1
      val releaseVersion = bundlePair._2
      V2Package(
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

    def rev(v2: V2Package): (V2Bundle, ReleaseVersion) =
      (V2Bundle(
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

    Bijection.build[(V2Bundle, ReleaseVersion), V2Package](fwd)(rev)
  }

  implicit val V3BundleToV3Package
  : Bijection[(V3Bundle, ReleaseVersion), V3Package] = {
    def fwd(bundlePair: (V3Bundle, ReleaseVersion)): V3Package = {
      val v3 = bundlePair._1
      val releaseVersion = bundlePair._2
      V3Package(
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

    def rev(v3: V3Package): (V3Bundle, ReleaseVersion) =
      (V3Bundle(
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

    Bijection.build[(V3Bundle, ReleaseVersion), V3Package](fwd)(rev)
  }

  implicit val BundleToPackage
  : Bijection[(BundleDefinition, ReleaseVersion), PackageDefinition] = {
    def fwd(bundlePair: (BundleDefinition, ReleaseVersion)): PackageDefinition = {
      val (bundle, releaseVersion) = bundlePair
      bundle match {
        case v2: V2Bundle => (v2, releaseVersion).as[V2Package]
        case v3: V3Bundle => (v3, releaseVersion).as[V3Package]
      }
    }

    def rev(packageDefinition: PackageDefinition): (BundleDefinition, ReleaseVersion) = {
      packageDefinition match {
        case v2: V2Package => v2.as[(V2Bundle, ReleaseVersion)]
        case v3: V3Package => v3.as[(V3Bundle, ReleaseVersion)]
      }
    }

    Bijection.build[(BundleDefinition, PackageDefinition.ReleaseVersion), PackageDefinition](fwd)(rev)
  }

  implicit val uriToString: Injection[Uri, String] = {
    Injection.build[Uri, String](_.toString)(s => Try(Uri.parse(s)))
  }

  implicit val v2PackagingVersionToString: Bijection[universe.v2.model.PackagingVersion, String] = {
    val fwd = (version: universe.v2.model.PackagingVersion) => version.toString
    val rev = universe.v2.model.PackagingVersion.apply _
    Bijection.build(fwd)(rev)
  }

  implicit val v3V2PackagingVersionToString: Injection[universe.v3.model.V2PackagingVersion.type, String] = {
    packagingVersionSubclassToString(universe.v3.model.V2PackagingVersion)
  }

  implicit val v3V3PackagingVersionToString: Injection[universe.v3.model.V3PackagingVersion.type, String] = {
    packagingVersionSubclassToString(universe.v3.model.V3PackagingVersion)
  }

  implicit val v3PackagingVersionToString: Injection[universe.v3.model.PackagingVersion, String] = {
    val fwd = (version: universe.v3.model.PackagingVersion) => version.show
    val rev = (s: String) => Try(universe.v3.model.PackagingVersion.allVersions(s))

    ConversionFailureInjection(Injection.build(fwd)(rev)) { from =>
      val values = universe.v3.model.PackagingVersion.allVersions.keys.mkString(", ")
      s"Expected one of [$values] for packaging version, but found [$from]"
    }
  }

  implicit val v3V2PackagingVersionToV2PackagingVersion =
    Injection.connect[universe.v3.model.V2PackagingVersion.type, String, universe.v2.model.PackagingVersion]

  implicit val v3V3PackagingVersionToV2PackagingVersion =
    Injection.connect[universe.v3.model.V3PackagingVersion.type, String, universe.v2.model.PackagingVersion]

  implicit val v3PackagingVersionToV2PackagingVersion =
    Injection.connect[universe.v3.model.PackagingVersion, String, universe.v2.model.PackagingVersion]

  private[this] def packagingVersionSubclassToString[V <: universe.v3.model.PackagingVersion](
    expected: V
  ): Injection[V, String] = {
    val fwd = (version: V) => version.show
    val rev = (version: String) => {
      if (version == expected.show) Success(expected)
      else Failure(new IllegalArgumentException(version))
    }

    ConversionFailureInjection(Injection.build(fwd)(rev)) { from =>
      s"Expected value [${expected.show}] for packaging version, but found [$from]"
    }
  }

  implicit val v2ReleaseVersionToString: Bijection[universe.v2.model.ReleaseVersion, String] = {
    val fwd = (x: universe.v2.model.ReleaseVersion) => x.toString
    val rev = (x: String) => universe.v2.model.ReleaseVersion(x)
    Bijection.build(fwd)(rev)
  }

  implicit val v3ReleaseVersionToInt: Injection[universe.v3.model.PackageDefinition.ReleaseVersion, Int] = {
    val fwd = (x: universe.v3.model.PackageDefinition.ReleaseVersion) => x.value
    val rev = universe.v3.model.PackageDefinition.ReleaseVersion.apply _

    ConversionFailureInjection(Injection.build(fwd)(rev)) { from =>
      s"Expected integer value >= 0 for release version, but found [$from]"
    }
  }

  implicit val v3ReleaseVersionToString = {
    Injection.connect[universe.v3.model.PackageDefinition.ReleaseVersion, Int, String]
  }

  implicit val v3ReleaseVersionToV2ReleaseVersion = {
    Injection.connect[universe.v3.model.PackageDefinition.ReleaseVersion, String, universe.v2.model.ReleaseVersion]
  }

}
