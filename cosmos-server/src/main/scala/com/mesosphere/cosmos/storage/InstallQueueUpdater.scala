package com.mesosphere.cosmos.storage

import cats.data.Xor

import com.netaporter.uri.Uri

import com.twitter.util.Future
import com.twitter.bijection.Conversion.asMethod

import com.mesosphere.cosmos.converter.Storage._
import com.mesosphere.cosmos.{ PackageNotFound, ZooKeeperStorageError }
import com.mesosphere.universe.v3.model.PackageDefinition
import com.mesosphere.cosmos.converter.Common.BundleToPackage
import com.mesosphere.universe.v3.model._

import io.circe.Encoder
import io.circe.jawn.decode

import org.apache.curator.framework.CuratorFramework

import java.nio.charset.StandardCharsets
import java.util.Base64

private[cosmos] final class UniverseInstallQueueUpdater(zkClient: CuratorFramework) extends InstallQueueUpdater[PackageDefinition] {

  val zkPath = InstallQueueHelpers.universeInstallQueue
}

private[cosmos] final class LocalInstallQueueUpdater(zkClient: CuratorFramework) extends InstallQueueUpdater[Uri] {

  val zkPath = InstallQueueHelpers.localInstallQueue
}

sealed trait InstallQueueUpdater[T] {

  val zkPath: String

  def deletePackage(
    zkClient: CuratorFramework,
    pkg: PackageCoordinate
  ): Future[Unit] = {

    val pkgPath = s"${zkPath}/${pkg.as[String]}"
    Future {
      if (zkClient.checkExists().forPath(pkgPath) != null) {
        zkClient.delete().deletingChildrenIfNeeded().forPath(pkgPath)
      }
    }
  }
}
