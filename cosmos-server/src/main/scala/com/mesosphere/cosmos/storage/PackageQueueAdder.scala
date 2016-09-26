package com.mesosphere.cosmos.storage

import com.netaporter.uri.Uri
import com.twitter.util.{ Future, Promise }
import com.twitter.bijection.Conversion.asMethod

import com.mesosphere.cosmos.converter.Storage._
import com.mesosphere.cosmos.ZooKeeperStorageError
import com.mesosphere.universe.v3.model.PackageDefinition
import com.mesosphere.universe.v3.circe.Encoders._

import io.circe.Encoder
import io.circe.jawn.decode
import io.circe.syntax._

import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.{ BackgroundCallback, CuratorEvent, CuratorEventType }

import org.apache.zookeeper.KeeperException

import java.nio.charset.StandardCharsets

private[cosmos] final class UniversePackageQueueAdder(zkClient: CuratorFramework) extends
  PackageQueueAdder[PackageDefinition](zkClient) {

  val zkPath = PackageQueueHelpers.universePackageQueue

  override def getBytes(pkgDef: PackageDefinition) =
    pkgDef.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
}

private[cosmos] final class LocalPackageQueueAdder(zkClient: CuratorFramework) extends
  PackageQueueAdder[Uri](zkClient) {

  val zkPath = PackageQueueHelpers.localPackageQueue

  override def getBytes(uri: Uri) = {
    uri.toString.getBytes(StandardCharsets.UTF_8)
  }
}

sealed abstract class PackageQueueAdder[T](zkClient: CuratorFramework) {

  val zkPath: String

  def getBytes(a: T): Array[Byte]

  def addPackage(
    pkg: PackageCoordinate,
    data: T
  ): Future[Boolean] = {

    val promise = Promise[Boolean]()
    val pkgPath = s"${zkPath}/${pkg.as[String]}"

    zkClient.create.creatingParentsIfNeeded.inBackground(
      new AddPackageHandler(promise, data)
    ).forPath(
      pkgPath,
      getBytes(data)
    )

    promise
  }

  private[this] final class AddPackageHandler(
    promise: Promise[Boolean],
    data: T
  ) extends  BackgroundCallback {
    private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

    override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
      if (event.getType == CuratorEventType.CREATE) {
        val code = KeeperException.Code.get(event.getResultCode)
        code match {
          case KeeperException.Code.OK =>
            promise.setValue(PackageQueueHelpers.newNode)
          case KeeperException.Code.NODEEXISTS =>
            promise.setValue(PackageQueueHelpers.nodeAlreadyExists)
          case _ =>
            promise.setException(KeeperException.create(code, event.getPath))
        }
      } else {
        logger.error("Repository storage create callback called for incorrect event: {}", event)
      }
    }
  }
}
