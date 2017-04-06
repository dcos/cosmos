package com.mesosphere.universe

import cats.syntax.either._
import com.mesosphere.universe.common.circe.Decoders._
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.HCursor

package v3.model {

  sealed abstract class PackagingVersion private[model](val show: String)

  object PackagingVersion {
    val allVersions = Seq(V2PackagingVersion, V3PackagingVersion)

    private[this] val allVersionsString = allVersions.map(_.show).mkString(", ")

    private[this] val allVersionsIndex: Map[String, PackagingVersion] = {
      allVersions.map(v => v.show -> v).toMap
    }

    def apply(s: String): Try[PackagingVersion] = {
      allVersionsIndex.get(s) match {
        case Some(v) => Return(v)
        case _ => Throw(new IllegalArgumentException(
          s"Expected one of [$allVersionsString] for packaging version, but found [$s]"
        ))
      }
    }

    implicit def encodePackagingVersion[A <: PackagingVersion]: Encoder[A] = {
      Encoder[String].contramap(version => version.show)
    }

    implicit val decodeV3PackagingVersion: Decoder[PackagingVersion] = {
      Decoder.instance[PackagingVersion] { (c: HCursor) =>
        c.as[String].map(PackagingVersion(_)).flatMap {
          case Return(v) => Right(v)
          case Throw(e) => Left(DecodingFailure(e.getMessage, c.history))
        }
      }
    }

    def packagingVersionSubclassToString[V <: PackagingVersion](
      expected: V
    ): Decoder[V] = {
      Decoder.instance { (c: HCursor) =>
        c.as[String].flatMap {
          case expected.show => Right(expected)
          case x => Left(DecodingFailure(
            s"Expected value [${expected.show}] for packaging version, but found [$x]",
            c.history
          ))
        }
      }
    }

  }

  case object V2PackagingVersion extends PackagingVersion("2.0") {
    implicit val encodeV3V2PackagingVersion: Encoder[V2PackagingVersion.type] = {
      PackagingVersion.encodePackagingVersion
    }
    implicit val decodeV3V2PackagingVersion: Decoder[V2PackagingVersion.type] = {
      PackagingVersion.packagingVersionSubclassToString(V2PackagingVersion)
    }
  }

  case object V3PackagingVersion extends PackagingVersion("3.0") {
    implicit val encodeV3V3PackagingVersion: Encoder[V3PackagingVersion.type] = {
      PackagingVersion.encodePackagingVersion
    }
    implicit val decodeV3V3PackagingVersion: Decoder[V3PackagingVersion.type] = {
      PackagingVersion.packagingVersionSubclassToString(V3PackagingVersion)
    }
  }

}
