package com.mesosphere.cosmos.storage

import com.netaporter.uri.Uri
import com.twitter.util.Future
import com.twitter.bijection.Conversion.asMethod

import com.mesosphere.cosmos.converter.Storage._
import com.mesosphere.cosmos.ZooKeeperStorageError
import com.mesosphere.universe.v3.model.PackageDefinition
import com.mesosphere.universe.v3.circe.Encoders._

import io.circe.Encoder
import io.circe.jawn.decode
import io.circe.syntax._

import org.apache.curator.framework.CuratorFramework

import java.nio.charset.StandardCharsets

private[cosmos] final class UniverseInstallQueueWriter(zkClient: CuratorFramework) extends InstallQueueWriter[PackageDefinition] {

  val zkPath = InstallQueueHelpers.universeInstallQueue

  override def getBytes(pkgDef: PackageDefinition) =
    pkgDef.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
}

private[cosmos] final class LocalInstallQueueWriter(zkClient: CuratorFramework) extends InstallQueueWriter[Uri] {

  val zkPath = InstallQueueHelpers.localInstallQueue

  override def getBytes(uri: Uri) = {
    uri.toString.getBytes(StandardCharsets.UTF_8)
  }
}

sealed trait InstallQueueWriter[T] {

  val zkPath: String

  def getBytes(a: T): Array[Byte]

  def addPackage(
    zkClient: CuratorFramework,
    pkg: PackageCoordinate,
    data: T
  ): Future[Unit] = {

    val pkgPath = s"${zkPath}/${pkg.as[String]}"
    Future {
      if (zkClient.checkExists().forPath(pkgPath) == null) {
          zkClient.create.creatingParentsIfNeeded.forPath(pkgPath)
          zkClient.setData().forPath(pkgPath, getBytes(data))
      }
    }
  }

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
