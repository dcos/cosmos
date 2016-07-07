package com.mesosphere.cosmos.converter

import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.bijection.{Bijection, Injection}

import scala.util.{Failure, Success, Try}

object Common {

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
