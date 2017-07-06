package com.mesosphere

import com.mesosphere.cosmos.Flaggables._
import com.mesosphere.cosmos.model.ZooKeeperUri
import com.netaporter.uri.Uri
import com.twitter.app.GlobalFlag
import com.twitter.conversions.storage._
import com.twitter.util.ScheduledThreadPoolTimer
import com.twitter.util.StorageUnit
import com.twitter.util.Timer
import java.net.InetSocketAddress
import java.nio.file.Path

package object cosmos {
  implicit val globalTimer: Timer = new ScheduledThreadPoolTimer()

  def getHttpInterface: Option[InetSocketAddress] = {
    httpInterface().orElse(
      _root_.io.github.benwhitehead.finch.httpInterface()
    )
  }

  def getHttpsInterface: Option[InetSocketAddress] = {
    httpsInterface.getWithDefault.orElse(
      _root_.io.github.benwhitehead.finch.httpsInterface.getWithDefault
    )
  }

  def getCertificatePath: Option[Path] = {
    certificatePath.getWithDefault.orElse(
      _root_.io.github.benwhitehead.finch.certificatePath.getWithDefault
    )
  }

  def getKeyPath: Option[Path] = {
    keyPath.getWithDefault.orElse(
      _root_.io.github.benwhitehead.finch.keyPath.getWithDefault
    )
  }
}

/* A flag's name is the fully-qualified classname. GlobalFlag doesn't support package object. We
 * must instead use regular package declarations
 */
package cosmos {
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

  object httpInterface extends GlobalFlag[Option[InetSocketAddress]](
    None,
    "The TCP Interface and port for the http server {[<hostname/ip>]:port}. (Set to " +
    "empty value to disable)"
  )

  object httpsInterface extends GlobalFlag[InetSocketAddress](
    "The TCP Interface and port for the https server {[<hostname/ip>]:port}. Requires " +
    s"-${certificatePath.name} and -${keyPath.name} to be set. (Set to empty value to disable)"
  )

  object certificatePath extends GlobalFlag[Path](
    "Path to PEM format SSL certificate file"
  )

  object keyPath extends GlobalFlag[Path](
    "Path to SSL Key file"
  )

  object maxClientResponseSize extends GlobalFlag[StorageUnit](
    5.megabytes,
    "Maximum size for the response for requests initiated by Cosmos in Megabytes"
  )
  // scalastyle:on object.name
}
