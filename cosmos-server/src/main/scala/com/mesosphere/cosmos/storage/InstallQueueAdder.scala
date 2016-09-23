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

private[cosmos] final class UniverseInstallQueueAdder(zkClient: CuratorFramework) extends InstallQueueAdder[PackageDefinition] {

  val zkPath = InstallQueueHelpers.universeInstallQueue

  override def getBytes(pkgDef: PackageDefinition) =
    pkgDef.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
}

private[cosmos] final class LocalInstallQueueAdder(zkClient: CuratorFramework) extends InstallQueueAdder[Uri] {

  val zkPath = InstallQueueHelpers.localInstallQueue

  override def getBytes(uri: Uri) = {
    uri.toString.getBytes(StandardCharsets.UTF_8)
  }
}

sealed trait InstallQueueAdder[T] {

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
}
