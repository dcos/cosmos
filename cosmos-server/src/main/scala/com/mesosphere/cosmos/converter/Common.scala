package com.mesosphere.cosmos.converter

import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.{Bijection, Injection}

import scala.util.{Failure, Success, Try}

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

  implicit val v3V2PackagingVersionToV2PackagingVersion: Injection[
    universe.v3.model.V2PackagingVersion.type,
    universe.v2.model.PackagingVersion
    ] = Injection.connect[universe.v3.model.V2PackagingVersion.type, String, universe.v2.model.PackagingVersion]

  implicit val v3V3PackagingVersionToV2PackagingVersion: Injection[
    universe.v3.model.V3PackagingVersion.type,
    universe.v2.model.PackagingVersion
    ] = Injection.connect[universe.v3.model.V3PackagingVersion.type, String, universe.v2.model.PackagingVersion]

  implicit val v3PackagingVersionToV2PackagingVersion: Injection[
    universe.v3.model.PackagingVersion,
    universe.v2.model.PackagingVersion
    ] = Injection.connect[universe.v3.model.PackagingVersion, String, universe.v2.model.PackagingVersion]

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
    val rev = twitterTryToScalaTry(universe.v3.model.PackageDefinition.ReleaseVersion.apply)

    ConversionFailureInjection(Injection.build(fwd)(rev)) { from =>
      s"Expected integer value >= 0 for release version, but found [$from]"
    }
  }

  implicit val v3ReleaseVersionToString: Injection[
    universe.v3.model.PackageDefinition.ReleaseVersion,
    String
    ] = Injection.connect[universe.v3.model.PackageDefinition.ReleaseVersion, Int, String]

  implicit val v3ReleaseVersionToV2ReleaseVersion: Injection[
    universe.v3.model.PackageDefinition.ReleaseVersion,
    universe.v2.model.ReleaseVersion
    ] = Injection.connect[universe.v3.model.PackageDefinition.ReleaseVersion, String, universe.v2.model.ReleaseVersion]

  private[this] def twitterTryToScalaTry[A, B](a: (A) => com.twitter.util.Try[B]): (A) => scala.util.Try[B] = {
    (aa: A) => a(aa) match {
      case com.twitter.util.Return(aaa) => Success(aaa)
      case com.twitter.util.Throw(e) => Failure(e)
    }
  }

}
