package com.mesosphere.cosmos.storage

import cats.data.Xor

import com.netaporter.uri.dsl._
import com.netaporter.uri.Uri

import com.twitter.util.{ Future, Promise }
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
import org.apache.curator.framework.api.{ BackgroundCallback, CuratorEvent, CuratorEventType }

import org.apache.zookeeper.KeeperException

import java.nio.charset.StandardCharsets
import java.util.Base64

import scala.collection.JavaConversions._

private[cosmos] final class UniversePackageQueueReader(zkClient: CuratorFramework) extends
  PackageQueueReader[PackageDefinition](zkClient) {

  val zkPath = PackageQueueHelpers.universePackageQueue

  override def getData(bytes: Array[Byte]): PackageDefinition = {
    val str = new String(bytes, StandardCharsets.UTF_8)
    decode[PackageDefinition](str) match {
      case Xor.Right(pkgDef) => pkgDef
      case Xor.Left(failure) => throw ZooKeeperStorageError(s"Couldn't parse package definition from Zookeeper: $str")
    }
  }

}

private[cosmos] final class LocalPackageQueueReader(zkClient: CuratorFramework) extends
  PackageQueueReader[Uri](zkClient) {

  val zkPath = PackageQueueHelpers.localPackageQueue

  override def getData(bytes: Array[Byte]): Uri = {
    new String(bytes, StandardCharsets.UTF_8)
  }
}

sealed abstract class PackageQueueReader[T](zkClient: CuratorFramework) {

  val zkPath: String

  def getData(bytes: Array[Byte]): T

  def readPackage(
    pkg: PackageCoordinate
  ): Future[Option[T]] = {

    val pkgPath = s"${zkPath}/${pkg.as[String]}"
    val promise = Promise[Option[T]]()

    zkClient.getData().inBackground(
      new ReadPackage(promise)
    ).forPath(
      pkgPath
    )

    promise
  }

  private final class ReadPackage(
    promise: Promise[Option[T]]
  ) extends  BackgroundCallback {

    private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

    override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
      if (event.getType == CuratorEventType.GET_DATA) {
	val code = KeeperException.Code.get(event.getResultCode)
	code match {
	  case KeeperException.Code.OK =>
	    promise.setValue(Some(getData(event.getData)))
	  case KeeperException.Code.NONODE =>
	    promise.setValue(None)
          case _ =>
	    promise.setException(KeeperException.create(code, event.getPath))
	}
      } else {
	logger.error("Package queue ReadPackage callback called for incorrect event: {}", event)
      }
    }
  }

  def queue(): Future[Option[List[PackageCoordinate]]] = {

    val promise = Promise[Option[List[PackageCoordinate]]]()

    zkClient.getChildren().inBackground(
      new ReadQueue(promise)
    ).forPath(
      zkPath
    )

    promise
  }

  private final class ReadQueue(
    promise: Promise[Option[List[PackageCoordinate]]]
  ) extends  BackgroundCallback {

    private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

    override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
      if (event.getType == CuratorEventType.CHILDREN) {
	val code = KeeperException.Code.get(event.getResultCode)
	code match {
	  case KeeperException.Code.OK =>
	    promise.setValue(Some(event.getChildren.toList.map(_.as[PackageCoordinate])))
	  case KeeperException.Code.NONODE =>
	    promise.setValue(None)
          case _ =>
	    promise.setException(KeeperException.create(code, event.getPath))
	}
      } else {
	logger.error("Package queue ReadQueue callback called for incorrect event: {}", event)
      }
    }
  }
}
