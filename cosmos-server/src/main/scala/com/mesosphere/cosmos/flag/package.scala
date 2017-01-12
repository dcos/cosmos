package com.mesosphere.cosmos

import com.mesosphere.cosmos.Flaggables._
import com.mesosphere.cosmos.model.ZooKeeperUri
import com.netaporter.uri.Uri
import com.twitter.app.GlobalFlag
import java.net.InetSocketAddress
import java.nio.file.Path

package object flag {
  // scalastyle:off object.name
  object dcosUri extends GlobalFlag[Uri](
    s"The URI where the DCOS Admin Router is located. If this flag is set, " +
    s"${mesosMasterUri.name} and ${marathonUri.name} will be ignored"
  )

  object adminRouterUri extends GlobalFlag[Uri](
    Uri.parse("http://master.mesos"),
      "The URI where AdminRouter can be found"
    )

  object marathonUri extends GlobalFlag[Uri](
    Uri.parse("http://master.mesos:8080"),
      "The URI where marathon can be found"
    )

  object mesosMasterUri extends GlobalFlag[Uri](
    Uri.parse("http://leader.mesos:5050"),
      "The URI where the leading Mesos master can be found"
    )

  object zookeeperUri extends GlobalFlag[ZooKeeperUri](
    ZooKeeperUri.parse("zk://127.0.0.1:2181/cosmos").get(),
      "The ZooKeeper connection string"
    )

  object packageStorageUri extends GlobalFlag[ObjectStorageUri](
    "The URI where packages are stored"
  )

  object stagedPackageStorageUri extends GlobalFlag[ObjectStorageUri](
    "The URI where packages are staged before permanent storage"
  )

  object httpInterface extends GlobalFlag[InetSocketAddress](
    "The TCP Interface and port for the http server {[<hostname/ip>]:port}. (Set to " +
    "empty value to disable)"
  )

  def getHttpInterface: Option[InetSocketAddress] = {
    // TODO: file issue to remove this when we change DC/OS to using this flag directly
    httpInterface.getWithDefault.orElse(
      _root_.io.github.benwhitehead.finch.httpInterface.getWithDefault
    )
  }

  object httpsInterface extends GlobalFlag[InetSocketAddress](
      "The TCP Interface and port for the https server {[<hostname/ip>]:port}. Requires " +
      s"-${certificatePath.name} and -${keyPath.name} to be set. (Set to empty value to disable)"
    )

  def getHttpsInterface: Option[InetSocketAddress] = {
    httpsInterface.getWithDefault.orElse(
      _root_.io.github.benwhitehead.finch.httpsInterface.getWithDefault
    )
  }

  object certificatePath extends GlobalFlag[Path](
    "Path to PEM format SSL certificate file"
  )

  def getCertificatePath: Option[Path] = {
    certificatePath.getWithDefault.orElse(
      _root_.io.github.benwhitehead.finch.certificatePath.getWithDefault
    )
  }

  object keyPath extends GlobalFlag[Path](
    "Path to SSL Key file"
  )

  def getKeyPath: Option[Path] = {
    keyPath.getWithDefault.orElse(
      _root_.io.github.benwhitehead.finch.keyPath.getWithDefault
    )
  }
  // scalastyle:on object.name
}
