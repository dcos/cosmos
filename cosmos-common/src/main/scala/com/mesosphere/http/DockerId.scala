package com.mesosphere.http

import cats.syntax.either._
import com.mesosphere.security.Digest
import fastparse.all._

final case class DockerId(
  hostAndPort: Option[String],
  name: String,
  tagOrDigest: Either[String, Digest]
)

final object DockerId {
  def parse(value: String): Option[DockerId] = {
    parser.parse(value).fold(
      (_, _, _) => None,
      (id, _) => Some(id)
    )
  }

  val parser: Parser[DockerId] = {
    val hostAndPort = {
      /* If the host and port is included it must match: $host:$port/
       * Notice how both host and port are required if specified.
       */
      val host = CharIn('a' to 'z', '0' to '9', ".").rep(1)
      val port = (":" ~ CharIn('0' to '9').rep(1))

      (host ~ port ~ &("/")).!.?
    }

    val repo = CharIn('a' to 'z', '0' to '9', "/").rep(1).!

    val tagOrDigest: Parser[Either[String, Digest]] = {
      val tag = ":" ~ AnyChar.rep(1).!.map(Either.left[String, Digest](_))
      val digest = "@" ~ Digest.parser.map(Either.right[String, Digest](_))

      (tag | digest)
    }

    P (
      (hostAndPort ~ repo ~ tagOrDigest).map {
        case (hostAndPort, name, tagOrDigest) => DockerId(hostAndPort, name, tagOrDigest)
      }
    )
  }
}
