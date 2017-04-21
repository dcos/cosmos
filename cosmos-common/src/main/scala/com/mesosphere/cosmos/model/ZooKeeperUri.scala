package com.mesosphere.cosmos.model

import com.twitter.util.{Return, Throw, Try}

import scala.util.matching.Regex

case class ZooKeeperUri private(connectString: String, path: String) {

  override def toString: String = s"zk://$connectString$path"

}

object ZooKeeperUri {

  private[this] val HostAndPort: String = """[A-z0-9-.]+(?::\d+)?"""
  private[this] val PathSegment: String = """[^/]+"""
  private[this] val ValidationRegex: Regex =
    s"""^zk://($HostAndPort(?:,$HostAndPort)*)(/$PathSegment(?:/$PathSegment)*)$$""".r

  def parse(s: String): Try[ZooKeeperUri] = {
    s match {
      case ValidationRegex(hosts, path) => Return(new ZooKeeperUri(hosts, path))
      case _ => Throw(new RuntimeException(s"ZooKeeper URI not parsable: $s"))
    }
  }

}
