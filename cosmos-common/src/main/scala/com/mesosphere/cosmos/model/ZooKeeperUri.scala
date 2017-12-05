package com.mesosphere.cosmos.model

import com.mesosphere.cosmos.error.ZooKeeperUriParseError
import scala.util.matching.Regex

case class ZooKeeperUri private(connectString: String, path: String) {

  override def toString: String = s"zk://$connectString$path"

}

object ZooKeeperUri {

  private[this] val HostAndPort: String = """[A-z0-9-.]+(?::\d+)?"""
  private[this] val PathSegment: String = """[^/]+"""
  private[this] val ValidationRegex: Regex =
    s"""^zk://($HostAndPort(?:,$HostAndPort)*)(/$PathSegment(?:/$PathSegment)*)$$""".r

  def parse(s: String): Either[ZooKeeperUriParseError, ZooKeeperUri] = {
    s match {
      case ValidationRegex(hosts, path) => Right(new ZooKeeperUri(hosts, path))
      case _ => Left(ZooKeeperUriParseError(s))
    }
  }

}
