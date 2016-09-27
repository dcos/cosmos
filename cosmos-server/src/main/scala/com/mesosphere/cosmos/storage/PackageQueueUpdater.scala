package com.mesosphere.cosmos.storage

import cats.data.Xor

import com.netaporter.uri.Uri

import com.twitter.util.{ Future, Promise }
import com.twitter.bijection.Conversion.asMethod

import com.mesosphere.cosmos.converter.Storage._
import com.mesosphere.cosmos.{ PackageNotFound, ZooKeeperStorageError }
import com.mesosphere.universe.v3.model.PackageDefinition
import com.mesosphere.cosmos.converter.Common.BundleToPackage
import com.mesosphere.universe.v3.model._

import io.circe.Encoder
import io.circe.jawn.decode

import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.{ BackgroundCallback, CuratorEvent, CuratorEventType }

import org.apache.zookeeper.KeeperException

import java.nio.charset.StandardCharsets
import java.util.Base64

private[cosmos] final class UniversePackageQueueUpdater(zkClient: CuratorFramework)
  extends PackageQueueUpdater[PackageDefinition](zkClient) {

  val zkPath = PackageQueueHelpers.universePackageQueue
}

private[cosmos] final class LocalPackageQueueUpdater(zkClient: CuratorFramework)
  extends PackageQueueUpdater[Uri](zkClient) {

  val zkPath = PackageQueueHelpers.localPackageQueue
}

sealed abstract class PackageQueueUpdater[T](zkClient: CuratorFramework) {

  val zkPath: String

  def deletePackage(
    pkg: PackageCoordinate
  ): Future[Boolean] = {

    val pkgPath = s"${zkPath}/${pkg.as[String]}"
    val promise = Promise[Boolean]()

    zkClient.delete.deletingChildrenIfNeeded.inBackground(
      new DeletePackage(promise)
    ).forPath(
      pkgPath
    )

    promise
  }

  private final class DeletePackage(
    promise: Promise[Boolean]
  ) extends  BackgroundCallback {

    private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

    override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
      if (event.getType == CuratorEventType.DELETE) {
	val code = KeeperException.Code.get(event.getResultCode)
	code match {
	  case KeeperException.Code.OK =>
	    promise.setValue(true)
	  case KeeperException.Code.NONODE =>
	    promise.setValue(false)
          case _ =>
	    promise.setException(KeeperException.create(code, event.getPath))
	}
      } else {
	logger.error("Repository storage read callback called for incorrect event: {}", event)
      }
    }
  }

}
