package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.InstallQueueError
import com.mesosphere.cosmos.OperationInProgress
import com.mesosphere.cosmos.converter.Common.packageCoordinateToBase64String
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.cosmos.storage.Envelope
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedDecoders._
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedEncoders._
import com.mesosphere.universe.bijection.BijectionUtils
import com.twitter.bijection.Conversion.asMethod
import com.twitter.finagle.stats.Stat.timeFuture
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Future
import com.twitter.util.Promise
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.BackgroundCallback
import org.apache.curator.framework.api.CuratorEvent
import org.apache.curator.framework.api.CuratorEventType
import org.apache.curator.framework.recipes.cache.ChildData
import org.apache.curator.framework.recipes.cache.TreeCache
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.data.Stat
import scala.collection.JavaConversions._

final class InstallQueue private(
  client: CuratorFramework
)(
  implicit statsReceiver: StatsReceiver
) extends ProcessorView with ProducerView with ReaderView with AutoCloseable {

  import InstallQueue._

  private[this] val stats = statsReceiver.scope("InstallQueue")

  private[this] val operationStatusCache = new TreeCache(client, installQueuePath)

  def start(): Unit = {
    operationStatusCache.start()
    ()
  }

  override def close(): Unit = {
    operationStatusCache.close()
  }

  override def next(): Future[Option[PendingOperation]] = {
    timeFuture(stats.stat("next")) {
      getAllCoordinates.flatMap { coordinates =>
        stats.stat("nodeCount").add(coordinates.length.toFloat)
        Future.collect(coordinates.map(getOperationIfPending)).map { operationsMaybePending =>
          val pendingOperations = operationsMaybePending.flatten
          if (pendingOperations.isEmpty) {
            None
          } else {
            // We return the earliest modified pending operations as
            // determined by the ZooKeeper mzxid. See
            // https://zookeeper.apache.org/doc/r3.2.2/zookeeperProgrammers.html#sc_timeInZk
            // for more information.
            Some(pendingOperations.minBy(_.stat.getMzxid).value)
          }
        }
      }
    }
  }

  private[this] def getOperationIfPending
  (
    packageCoordinate: PackageCoordinate
  ): Future[Option[WithZkStat[PendingOperation]]] = {
    // this method exists because we need to associate the package coordinate with
    // the result of getOperationStatus. This is really unreadable when inlined
    // because there are many layers of maps. While we are at it, we also
    // convert the OperationStatus to a PendingOperation, since the PendingOperation
    // already has a PackageCoordinate field. This is nicer than returning a tuple
    // of PackageCoordinate and OperationStatus.
    getOperationStatus(packageCoordinate).map { maybeOperationStatus =>
      maybeOperationStatus.flatMap {
        case WithZkStat(stat, Pending(operation, failure)) =>
          Some(WithZkStat(
            stat,
            PendingOperation(packageCoordinate, operation, failure)
          ))
        case _ => None
      }
    }
  }

  private[this] def getAllCoordinates: Future[List[PackageCoordinate]] = {
    val promise = Promise[List[PackageCoordinate]]()
    client.getChildren.inBackground(
      handler(promise, CuratorEventType.CHILDREN) {
        case (KeeperException.Code.OK, event) =>
          val coordinates =
            event.getChildren.toList.map { encodedCoordinate =>
              BijectionUtils.scalaTryToTwitterTry(
                encodedCoordinate.as[scala.util.Try[PackageCoordinate]]
              )
            }
          Try.collect(coordinates).map(_.toList)
        case (KeeperException.Code.NONODE, _) =>
          Return(List())
        case (code, event) =>
          Throw(KeeperException.create(code, event.getPath))
      }
    ).forPath(
      installQueuePath
    )
    promise
  }

  override def failure
  (
    packageCoordinate: PackageCoordinate,
    error: ErrorResponse
  ): Future[Unit] = {
    timeFuture(stats.stat("failure")) {
      getOperationStatus(packageCoordinate).flatMap {
        case None =>
          val message =
            "Attempted to signal failure on an " +
              s"operation not in the install queue: $packageCoordinate"
          Future.exception(InstallQueueError(message))
        case Some(WithZkStat(_, Failed(_))) =>
          val message =
            "Attempted to signal failure on an " +
              s"operation that has failed: $packageCoordinate"
          Future.exception(InstallQueueError(message))
        case Some(WithZkStat(stat, Pending(operation, _))) =>
          setOperationStatus(
            packageCoordinate,
            Failed(OperationFailure(operation, error)),
            stat.getVersion
          )
      }
    }
  }

  private[this] def getOperationStatus
  (
    packageCoordinate: PackageCoordinate
  ): Future[Option[WithZkStat[OperationStatus]]] = {
    val promise = Promise[Option[WithZkStat[OperationStatus]]]()
    client.getData.inBackground(
      handler(promise, CuratorEventType.GET_DATA) {
        case (KeeperException.Code.OK, event) =>
          Try(Envelope.decodeData[OperationStatus](event.getData))
            .map { operationStatus =>
              Some(
                WithZkStat(
                  event.getStat,
                  operationStatus
                )
              )
            }
        case (KeeperException.Code.NONODE, _) =>
          Return(None)
        case (code, event) =>
          Throw(KeeperException.create(code, event.getPath))
      }
    ).forPath(
      statusPath(packageCoordinate)
    )
    promise
  }

  private[this] def setOperationStatus
  (
    packageCoordinate: PackageCoordinate,
    operationStatus: OperationStatus,
    version: Int
  ): Future[Unit] = {
    val promise = Promise[Unit]()

    val data = Envelope.encodeData(operationStatus)
    stats.stat("nodeSize").add(data.length.toFloat)

    client.setData().withVersion(version).inBackground(
      handler(promise, CuratorEventType.SET_DATA) {
        case (KeeperException.Code.OK, _) =>
          Return(())
        case (code, event) =>
          Throw(KeeperException.create(code, event.getPath))
      }
    ).forPath(
      statusPath(packageCoordinate),
      data
    )
    promise
  }

  override def success
  (
    packageCoordinate: PackageCoordinate
  ): Future[Unit] = {
    timeFuture(stats.stat("success")) {
      getOperationStatus(packageCoordinate).flatMap {
        case None =>
          val message =
            "Attempted to signal success on an " +
              s"operation not in the install queue: $packageCoordinate"
          Future.exception(InstallQueueError(message))
        case Some(WithZkStat(_, Failed(_))) =>
          val message =
            "Attempted to signal success on an " +
              s"operation that has failed: $packageCoordinate"
          Future.exception(InstallQueueError(message))
        case Some(WithZkStat(stat, Pending(_, _))) =>
          deleteOperationStatus(packageCoordinate, stat.getVersion)
      }
    }
  }

  private[this] def deleteOperationStatus
  (
    packageCoordinate: PackageCoordinate,
    version: Int
  ): Future[Unit] = {
    val promise = Promise[Unit]()
    client.delete().withVersion(version).inBackground(
      handler(promise, CuratorEventType.DELETE) {
        case (KeeperException.Code.OK, _) =>
          Return(())
        case (code, event) =>
          Throw(KeeperException.create(code, event.getPath))
      }
    ).forPath(
      statusPath(packageCoordinate)
    )
    promise
  }

  override def add(packageCoordinate: PackageCoordinate, operation: Operation): Future[Unit] = {
    timeFuture(stats.stat("add")) {
      createOperationStatus(
        packageCoordinate,
        Pending(operation, None)
      ).rescue {
        case _: KeeperException.NodeExistsException =>
          setOperationInOperationStatus(packageCoordinate, operation)
      }
    }
  }

  private[this] def createOperationStatus
  (
    packageCoordinate: PackageCoordinate,
    operationStatus: OperationStatus
  ): Future[Unit] = {
    val promise = Promise[Unit]()

    val data = Envelope.encodeData(operationStatus)
    stats.stat("nodeSize").add(data.length.toFloat)

    client.create().creatingParentsIfNeeded().inBackground(
      handler(promise, CuratorEventType.CREATE) {
        case (KeeperException.Code.OK, _) =>
          Return(())
        case (code, event) =>
          Throw(KeeperException.create(code, event.getPath))
      }
    ).forPath(
      statusPath(packageCoordinate),
      data
    )
    promise
  }

  private[this] def setOperationInOperationStatus
  (
    packageCoordinate: PackageCoordinate,
    operation: Operation
  ): Future[Unit] = {
    getOperationStatus(packageCoordinate).flatMap {
      case None =>
        /* if we reach this statement, that means there was
         * an operation when the user made the request. The operation has
         * since completed which means that it was pending.
         * It is correct to return Throw(OperationInProgress), because
         * the request to add overlaps the existence of a pending operation.
         *
         * |------------|----------------------------|
         * |add starts  |                            |
         * |------------|----------------------------|
         * |Create      |  Node Exists               |
         * |            |  Contents = ?              |
         * |            |.............................
         * |            |  Node Exists               |
         * |            |  Contents = Pending        |
         * |            |                            |
         * |            |  Note:                     |
         * |            |  This must be Pending      |
         * |            |  otherwise node would not  |
         * |            |  have been deleted         |
         * |            |                            |
         * |------------|----------------------------|
         * |Set         |  Node does not Exist       |
         * |            |                            |
         * |            |                            |
         * |------------|----------------------------|
         * |add ends    |                            |
         * |------------|----------------------------|
         */
        Future.exception(OperationInProgress(packageCoordinate))
      case Some(WithZkStat(_, Pending(_, _))) =>
        Future.exception(OperationInProgress(packageCoordinate))
      case Some(WithZkStat(stat, Failed(failure))) =>
        setOperationStatus(
          packageCoordinate,
          Pending(operation, Some(failure)),
          stat.getVersion
        ).before(Future.Unit)
    }
  }

  override def viewStatus(): Future[Map[PackageCoordinate, OperationStatus]] = {
    timeFuture(stats.stat("viewStatus")) {
      val operationStatus =
        Option(operationStatusCache
          .getCurrentChildren(installQueuePath))
          .getOrElse(new java.util.HashMap[String, ChildData]())
          .toMap
          .map { case (encodedPackageCoordinate, childData) =>
            for {
              coordinate <- BijectionUtils.scalaTryToTwitterTry(
                encodedPackageCoordinate.as[scala.util.Try[PackageCoordinate]]
              )
              operationStatus <- Try(Envelope.decodeData[OperationStatus](childData.getData))
            } yield coordinate -> operationStatus
          }
          .toSeq
      Future.const(Try.collect(operationStatus).map(_.toMap))
    }
  }

}

object InstallQueue {

  val installQueuePath = "/package/task-queue"

  def statusPath(packageCoordinate: PackageCoordinate): String = {
    s"$installQueuePath/${packageCoordinate.as[String]}"
  }

  def apply(client: CuratorFramework)(implicit statsReceiver: StatsReceiver): InstallQueue = {
    val installQueue = new InstallQueue(client)
    installQueue.start()
    installQueue
  }

  private case class WithZkStat[A](stat: Stat, value: A)

  private def handler[A](promise: Promise[A], expectedEventType: CuratorEventType)(
    handle: (KeeperException.Code, CuratorEvent) => Try[A]
  ): BackgroundCallback = {
    new BackgroundCallback {
      override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
        if (event.getType == expectedEventType) {
          val code = KeeperException.Code.get(event.getResultCode)
          promise.update(handle(code, event))
        } else {
          promise.setException(InstallQueueError("Called handler for incorrect event type"))
        }
      }
    }
  }

}


