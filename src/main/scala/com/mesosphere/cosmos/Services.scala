package com.mesosphere.cosmos

import java.net.{Inet6Address, Inet4Address, InetAddress}

import com.netaporter.uri.Uri
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, Service}

object Services {
  def adminRouterClient(uri: Uri): Service[Request, Response] = {
    extractHostAndPort(uri) match {
      case Some(hostPort) => Http.client.newService(hostPort, "adminRouter")
      case _ => throw new IllegalArgumentException(s"unable to connect to '${uri.toStringRaw}'.")
    }
  }

  private[cosmos] def extractHostAndPort(uri: Uri): Option[String] = {
    val ip = uri.host flatMap { hostNameOrIp =>
      InetAddress.getByName(hostNameOrIp) match {
        case v4: Inet4Address => Some(v4.getHostAddress)
        case v6: Inet6Address => Some(s"[${v6.getHostAddress}]")
        case _ => None
      }
    }
    val hostPort = (uri.scheme, ip, uri.port) match {
      case (_, Some(h), Some(p)) => Some(s"$h:$p")
      case (Some(s), Some(h), None) => schemeDefaultPorts.get(s) map { p => s"$h:$p" }
      case (_, None, _) => None
      case (None, Some(h), None) => None
    }
    hostPort
  }

  private[this] val schemeDefaultPorts: Map[String, Int] = Map(
    "http" -> 80,
    "https" -> 443
  )
}
