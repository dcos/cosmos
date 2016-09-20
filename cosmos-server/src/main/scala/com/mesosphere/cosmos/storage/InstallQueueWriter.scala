package com.mesosphere.cosmos.storage

import com.netaporter.uri.Uri
import com.twitter.util.Future
import com.twitter.bijection.Conversion.asMethod

import com.mesosphere.universe.v3.model.PackageDefinition
import com.mesosphere.universe.v3.circe.Encoders._
import com.mesosphere.cosmos.converter.Storage._

import io.circe.Encoder
import io.circe.jawn.decode
import io.circe.syntax._

import org.apache.curator.framework.CuratorFramework

import java.nio.charset.StandardCharsets

class LocalInstallQueueWriter(zkClient: CuratorFramework) extends InstallQueueWriter[PackageDefinition] {

  val installQueue = InstallQueueHelpers.localInstallQueue

  override def getBytes(pkgDef: PackageDefinition) =
    pkgDef.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
}

class UniverseInstallQueueWriter(zkClient: CuratorFramework) extends InstallQueueWriter[Uri] {

  val installQueue = InstallQueueHelpers.universeInstallQueue

  override def getBytes(uri: Uri) =  uri.toString.getBytes(StandardCharsets.UTF_8)
}

trait InstallQueueWriter[T] {

  val installQueue: String

  def getBytes(a: T): Array[Byte]

  def addPackage(
    zkClient: CuratorFramework,
    pkg: PackageCoordinate,
    data: T
  ): Future[Unit] = {

    // readPackage -> add if needed
    val pkgPath = s"${installQueue}/${pkg.as[String]}"
    Future {
      if (zkClient.checkExists().forPath(pkgPath) == null) {
        zkClient.setData().forPath(pkgPath, getBytes(data))
      }
    }
  }
}
