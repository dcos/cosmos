package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.ZooKeeperStorageError
import com.mesosphere.cosmos.converter.Storage._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedDecoders._
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedEncoders._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.{Future, Promise}
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.{BackgroundCallback, CuratorEvent, CuratorEventType}
import org.apache.zookeeper.KeeperException

import scala.collection.JavaConversions._

final class PackageQueue(zkClient: CuratorFramework) extends
  PackageQueueAdder with PackageQueueReader with PackageQueueRemover
{

  import PackageQueueHelpers._

  override def add(
    pkg: PackageCoordinate,
    content: PackageQueueContents
  ): Future[PackageAddResult] = {

    val promise = Promise[PackageAddResult]()
    val pkgPath = s"$packageQueueBase/${pkg.as[String]}"

    zkClient.create.creatingParentsIfNeeded.inBackground(
      new AddHandler(promise)
    ).forPath(
      pkgPath,
      Envelope.encodeData(content)
    )

    promise

  }

  private[this] final class AddHandler(
    promise: Promise[PackageAddResult]
  ) extends  BackgroundCallback {

    private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

    override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
      if (event.getType == CuratorEventType.CREATE) {
        val code = KeeperException.Code.get(event.getResultCode)
        code match {
          case KeeperException.Code.OK =>
            promise.setValue(Created)
          case KeeperException.Code.NODEEXISTS =>
            promise.setValue(AlreadyExists)
          case _ =>
            promise.setException(KeeperException.create(code, event.getPath))
        }
      } else {
        logger.error("Repository storage create callback called for incorrect event: {}", event)
      }
    }
  }

  private def readError(
    pkg: PackageCoordinate
  ): Future[Option[ErrorResponse]] = {

    val pkgPath = s"$errorQueueBase/${pkg.as[String]}"
    val promise = Promise[Option[ErrorResponse]]()

    zkClient.getData().inBackground(
      new ReadErrorHandler(promise)
    ).forPath(
      pkgPath
    )

    promise

  }

  private final class ReadErrorHandler(
    promise: Promise[Option[ErrorResponse]]
  ) extends  BackgroundCallback {

    private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

    override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
      if (event.getType == CuratorEventType.GET_DATA) {
	val code = KeeperException.Code.get(event.getResultCode)
	code match {
	  case KeeperException.Code.OK =>
	    promise.setValue(Some(Envelope.decodeData[ErrorResponse](event.getData)))
	  case KeeperException.Code.NONODE =>
	    promise.setValue(None)
          case _ =>
	    promise.setException(KeeperException.create(code, event.getPath))
	}
      } else {
	logger.error("Package queue read callback called for incorrect event: {}", event)
      }
    }
  }

  override def read(
    pkg: PackageCoordinate
  ): Future[PackageQueueState] = {

    readPackageContents(pkg).join(readError(pkg))

  }

  private def readPackageContents(
    pkg: PackageCoordinate
  ): Future[Option[PackageQueueContents]] = {

    val pkgPath = s"$packageQueueBase/${pkg.as[String]}"
    val promise = Promise[Option[PackageQueueContents]]()

    zkClient.getData().inBackground(
      new ReadPackageHandler(promise)
    ).forPath(
      pkgPath
    )

    promise

  }

  private final class ReadPackageHandler(
    promise: Promise[Option[PackageQueueContents]]
  ) extends  BackgroundCallback {

    private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

    override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
      if (event.getType == CuratorEventType.GET_DATA) {
	val code = KeeperException.Code.get(event.getResultCode)
	code match {
	  case KeeperException.Code.OK =>
	    promise.setValue(Some(Envelope.decodeData[PackageQueueContents](event.getData)))
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

  override def readAll(): Future[Map[PackageCoordinate, PackageQueueState]] = {

    val promise = Promise[Map[PackageCoordinate, PackageQueueState]]()

    zkClient.getChildren().inBackground(
      new ReadAllHandler(promise)
    ).forPath(
      packageQueueBase
    )

    promise

  }

  private final class ReadAllHandler(
    promise: Promise[Map[PackageCoordinate, PackageQueueState]]
  ) extends  BackgroundCallback {

    private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

    override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
      if (event.getType == CuratorEventType.CHILDREN) {
	val code = KeeperException.Code.get(event.getResultCode)
	code match {
	  case KeeperException.Code.OK =>
	    val coords = event.getChildren.toList.map(_.as[PackageCoordinate])
	    Future.collect {
              coords.map(read(_))
            }.map(contents => promise.setValue(coords.zip(contents.toList).toMap))
            return
	  case KeeperException.Code.NONODE =>
	    promise.setValue(Map())
          case _ =>
	    promise.setException(KeeperException.create(code, event.getPath))
	}
      } else {
	logger.error("Package queue ReadQueue callback called for incorrect event: {}", event)
      }
    }
  }

  def done(
    pkg: PackageCoordinate,
    error: Option[ErrorResponse]
  ): Future[Unit] = {

    val pkgPath = s"$packageQueueBase/${pkg.as[String]}"
    val errPath = s"$errorQueueBase/${pkg.as[String]}"

    val transaction = zkClient.inTransaction().delete().forPath(pkgPath).and()
    error match {
      case Some(e) => transaction.create().forPath(pkgPath, Envelope.encodeData(e)).and()
      case None => transaction.delete().forPath(errPath).and()
    }

    try {
      // TODO: verify results from commit are what we expect
      transaction.commit()
      Future.Unit
    } catch {
      case e: Exception => throw ZooKeeperStorageError(e.getMessage)
    }
  }

}

object PackageQueueHelpers {

  val packageQueueBase = "/package/packageQueue"
  val errorQueueBase = "/package/packageQueueErrors"
  type PackageQueueState =  (Option[PackageQueueContents], Option[ErrorResponse])

}
