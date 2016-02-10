package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.model.PackageSource
import com.netaporter.uri.Uri
import com.twitter.io.Charsets
import com.twitter.util.Future
import io.circe.syntax._
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.KeeperException.NoNodeException

private[cosmos] final class ZooKeeperStorage(zkClient: CuratorFramework, defaultUniverseUri: Uri)
  extends PackageSourcesStorage {

  import ZooKeeperStorage._

  private[this] val DefaultSources: List[PackageSource] =
    List(PackageSource("Universe", defaultUniverseUri))

  // TODO cruhland: Tests for this class
  // TODO cruhland: Use a FuturePool

  /* TODO (jsancio): This is not correct. We never update Zookeeper with the default
   * value. Issue #191
   */
  def read(): Future[List[PackageSource]] = {
    Future(zkClient.getData.forPath(PackageSourcesPath))
      .map { bytes =>
        decodeOrThrow[List[PackageSource]](new String(bytes, Charsets.Utf8))
      }
      .handle {
        case e: NoNodeException => DefaultSources
      }
  }

  def write(sources: List[PackageSource]): Future[List[PackageSource]] = {
    val encodedSources = sources.asJson.noSpaces.getBytes(Charsets.Utf8)
    Future(zkClient.setData.forPath(PackageSourcesPath, encodedSources)) rescue {
      case e: NoNodeException =>
        Future {
          zkClient.create.creatingParentsIfNeeded.forPath(PackageSourcesPath, encodedSources)
        }
    } flatMap { _  =>
      // TODO (jsancio): We are ignoring Stat information for now. Issue #192
      read()
    }
  }
}

private object ZooKeeperStorage {

  private val PackageSourcesPath: String = "/package-sources/v1"

}
