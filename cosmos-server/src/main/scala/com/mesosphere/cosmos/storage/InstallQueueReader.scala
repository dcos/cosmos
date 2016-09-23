package com.mesosphere.cosmos.storage

import cats.data.Xor

import com.netaporter.uri.dsl._
import com.netaporter.uri.Uri

import com.twitter.util.Future
import com.twitter.bijection.Conversion.asMethod

import com.mesosphere.cosmos.converter.Storage._
import com.mesosphere.cosmos.{ PackageNotFound, ZooKeeperStorageError }
import com.mesosphere.universe.v3.model.PackageDefinition
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.cosmos.converter.Common.BundleToPackage
import com.mesosphere.universe.v3.model._

import io.circe.Encoder
import io.circe.jawn.decode

import org.apache.curator.framework.CuratorFramework

import java.nio.charset.StandardCharsets
import java.util.Base64

import scala.collection.JavaConversions._

private[cosmos] final class UniverseInstallQueueReader(zkClient: CuratorFramework) extends InstallQueueReader[PackageDefinition] {

  val zkPath = InstallQueueHelpers.universeInstallQueue

  override def getData(bytes: Array[Byte]): PackageDefinition = {
    val str = new String(bytes, StandardCharsets.UTF_8)
    decode[PackageDefinition](str) match {
      case Xor.Right(pkgDef) => pkgDef
      case Xor.Left(failure) => throw ZooKeeperStorageError(s"Couldn't parse package definition from Zookeeper: $str")
    }
  }

}

private[cosmos] final class LocalInstallQueueReader(zkClient: CuratorFramework) extends InstallQueueReader[Uri] {

  val zkPath = InstallQueueHelpers.localInstallQueue

  override def getData(bytes: Array[Byte]): Uri = {
    new String(bytes, StandardCharsets.UTF_8).as[Uri]
  }
}

sealed trait InstallQueueReader[T] {

  val zkPath: String

  def getData(bytes: Array[Byte]): T

  def readPackage(
    zkClient: CuratorFramework,
    pkg: PackageCoordinate
  ): Future[T] = {

    val pkgPath = s"${zkPath}/${pkg.as[String]}"
    Future {
      if (zkClient.checkExists().forPath(pkgPath) != null) {
        getData(zkClient.getData().forPath(pkgPath))
      } else {
        throw PackageNotFound(pkg.name)
      }
    }
  }

  def queue(zkClient: CuratorFramework): Future[List[PackageCoordinate]] = {
    Future {
      zkClient.getChildren().forPath(zkPath).toList.map(_.as[PackageCoordinate])
    }
  }
}
