package com.mesosphere.security

import fastparse.all._
import io.netty.buffer.ByteBufUtil

final case class Digest(
    algorithm: String,
    digest: Array[Byte]
) {
  override def toString(): String = {
    s"$algorithm:${ByteBufUtil.hexDump(digest)}"
  }
}

object Digest {
  def parse(value: String): Option[Digest] = {
    parser.parse(value).fold(
      (_, _, _) => None,
      (digest, _) => Some(digest)
    )
  }


  val parser: Parser[Digest] = {
    val algorithm = CharIn('a' to 'z', '0' to '9').rep(1).!
    val digest = CharIn('0' to '9', 'a' to 'f').rep(1).!

    P (
      (algorithm ~ ":" ~ digest).map {
        case( algorithm, digest) =>
          Digest(algorithm, ByteBufUtil.decodeHexDump(digest))
      }
    )
  }
}
