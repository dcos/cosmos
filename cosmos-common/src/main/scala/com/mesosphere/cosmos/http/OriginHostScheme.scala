package com.mesosphere.cosmos.http

case class OriginHostScheme(
  host: String,
  port: Option[String],
  urlScheme: OriginHostScheme.Scheme
) {
  def rawHost: String = {
    val portString = port.map(":" + _).getOrElse("")
    s"$host$portString"
  }

  def hostAndPort: String = {
    val derivedPort = port.getOrElse(urlScheme.defaultPort)
    s"$host:$derivedPort"
  }
}

object OriginHostScheme {
  def apply(hostAndPort: String, urlScheme: Scheme): OriginHostScheme = {
    val parts = hostAndPort.split(':')
    val port = if (parts.length > 1) {
      Some(parts(1))
    } else {
      None
    }

    OriginHostScheme(parts(0), port, urlScheme)
  }

  sealed trait Scheme {
    val defaultPort: String
  }

  object Scheme {
    val http: Scheme = Http
    val https: Scheme = Https

    def apply(value: String): Option[Scheme] = value match {
      case "http" => Option(http)
      case "https" => Option(https)
      case _ => None
    }
  }

  final object Http extends Scheme {
    override val defaultPort: String = "80"
  }

  final object Https extends Scheme {
    override val defaultPort: String = "443"
  }
}
