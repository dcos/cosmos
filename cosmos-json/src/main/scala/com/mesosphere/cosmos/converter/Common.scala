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
    val fwd = (version: universe.v3.model.V2PackagingVersion.type) => version.show
    val rev = stringToVersion(universe.v3.model.V2PackagingVersion) _
    Injection.build(fwd)(rev)
  }

  implicit val v3V3PackagingVersionToString: Injection[universe.v3.model.V3PackagingVersion.type, String] = {
    val fwd = (version: universe.v3.model.V3PackagingVersion.type) => version.show
    val rev = stringToVersion(universe.v3.model.V3PackagingVersion) _
    Injection.build(fwd)(rev)
  }

  implicit val v3PackagingVersionToString: Injection[universe.v3.model.PackagingVersion, String] = {
    val fwd = (version: universe.v3.model.PackagingVersion) => version.show

    val rev = (s: String) => {
      universe.v3.model.PackagingVersion.allVersions.get(s) match {
        case Some(version) =>
          Success(version)
        case _ =>
          val values = universe.v3.model.PackagingVersion.allVersions.keys.mkString(", ")
          val message = s"Expected one of [$values] for packaging version, but found [$s]"
          Failure(ConversionFailure(message))
      }
    }

    Injection.build(fwd)(rev)
  }

  implicit val v3V2PackagingVersionToV2PackagingVersion =
    Injection.connect[universe.v3.model.V2PackagingVersion.type, String, universe.v2.model.PackagingVersion]

  implicit val v3V3PackagingVersionToV2PackagingVersion =
    Injection.connect[universe.v3.model.V3PackagingVersion.type, String, universe.v2.model.PackagingVersion]

  implicit val v3PackagingVersionToV2PackagingVersion =
    Injection.connect[universe.v3.model.PackagingVersion, String, universe.v2.model.PackagingVersion]

  private[this] def stringToVersion[V <: universe.v3.model.PackagingVersion](expected: V)(
    version: String
  ): Try[V] = {
    if (version == expected.show) Success(expected)
    else {
      val message = s"Expected value [${expected.show}] for packaging version, but found [$version]"
      Failure(ConversionFailure(message))
    }
  }

}
