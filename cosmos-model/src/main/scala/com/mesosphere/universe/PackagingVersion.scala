package com.mesosphere.universe

import cats.syntax.either._
import com.mesosphere.universe
import com.mesosphere.universe.common.circe.Decoders._
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.HCursor
import io.circe.syntax.EncoderOps

package v3.model {

  sealed trait PackagingVersion {
    val show: String
  }

  object PackagingVersion {

    val allVersions: Seq[PackagingVersion] =
      Seq(
        universe.v3.model.V2PackagingVersion,
        universe.v3.model.V3PackagingVersion
      )

    private[this] val allVersionsString: String =
      allVersions.map(_.show).mkString(", ")

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

    implicit val decodePackagingVersion: Decoder[PackagingVersion] = {
      Decoder.instance[PackagingVersion] { (c: HCursor) =>
        c.as[String].map(PackagingVersion(_)).flatMap {
          case Return(v) => Right(v)
          case Throw(e) => Left(DecodingFailure(e.getMessage, c.history))
        }
      }
    }

    implicit val encodePackagingVersion: Encoder[PackagingVersion] = {
      Encoder.instance { packagingVersion: PackagingVersion =>
        packagingVersion match {
          case universe.v3.model.V2PackagingVersion =>
            universe.v3.model.V2PackagingVersion.asJson
          case universe.v3.model.V3PackagingVersion =>
            universe.v3.model.V3PackagingVersion.asJson
        }
      }
    }

  }

  case object V2PackagingVersion
    extends universe.v3.model.PackagingVersion
      with universe.v4.model.PackagingVersion {

    val show: String = "2.0"

    implicit val decodeV2PackagingVersion: Decoder[V2PackagingVersion.type] = {
      universe.v4.model.PackagingVersion.decodePackagingVersionSubclass(this)
    }

    implicit val encodeV2PackagingVersion: Encoder[V2PackagingVersion.type] = {
      Encoder.instance { _: V2PackagingVersion.type =>
        show.asJson
      }
    }
  }

  case object V3PackagingVersion
    extends universe.v3.model.PackagingVersion
      with universe.v4.model.PackagingVersion {

    val show: String = "3.0"

    implicit val decodeV3PackagingVersion: Decoder[V3PackagingVersion.type] = {
      universe.v4.model.PackagingVersion.decodePackagingVersionSubclass(this)
    }

    implicit val encodeV3PackagingVersion: Encoder[V3PackagingVersion.type] = {
      Encoder.instance { _: V3PackagingVersion.type =>
        show.asJson
      }
    }
  }

}

package v4.model {

  sealed trait PackagingVersion {
    val show: String
  }

  object PackagingVersion {

    val allVersions: Seq[PackagingVersion] =
      Seq(
        universe.v3.model.V2PackagingVersion,
        universe.v3.model.V3PackagingVersion,
        universe.v4.model.V4PackagingVersion
      )

    private[this] val allVersionsString: String =
      allVersions.map(_.show).mkString(", ")

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

    implicit val decodePackagingVersion: Decoder[PackagingVersion] = {
      Decoder.instance[PackagingVersion] { (c: HCursor) =>
        c.as[String].map(PackagingVersion(_)).flatMap {
          case Return(v) => Right(v)
          case Throw(e) => Left(DecodingFailure(e.getMessage, c.history))
        }
      }
    }

    implicit val encodePackagingVersion: Encoder[PackagingVersion] = {
      Encoder.instance { packagingVersion: PackagingVersion =>
        packagingVersion match {
          case universe.v3.model.V2PackagingVersion =>
            universe.v3.model.V2PackagingVersion.asJson
          case universe.v3.model.V3PackagingVersion =>
            universe.v3.model.V3PackagingVersion.asJson
          case universe.v4.model.V4PackagingVersion =>
            universe.v4.model.V4PackagingVersion.asJson
        }
      }
    }

    def decodePackagingVersionSubclass[V <: PackagingVersion](expected: V): Decoder[V] = {
      Decoder.instance { c: HCursor =>
        c.as[String].flatMap {
          case expected.show => Right(expected)
          case s => Left(
            DecodingFailure(
              s"Expected value [${expected.show}] for packaging version, but found [$s]",
              c.history
            )
          )
        }
      }
    }
  }


  case object V4PackagingVersion extends universe.v4.model.PackagingVersion {

    val show: String = "4.0"

    implicit val decodeV4V4PackagingVersion: Decoder[V4PackagingVersion.type] = {
      universe.v4.model.PackagingVersion.decodePackagingVersionSubclass(this)
    }

    implicit val encodeV4V4PackagingVersion: Encoder[V4PackagingVersion.type] = {
      Encoder.instance { _: V4PackagingVersion.type =>
        show.asJson
      }
    }
  }

}
