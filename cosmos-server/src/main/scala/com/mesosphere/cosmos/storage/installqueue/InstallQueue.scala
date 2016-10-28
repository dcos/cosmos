package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.converter.Common._
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.cosmos.storage.Envelope
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedDecoders._
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedEncoders._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future
import com.twitter.util.Promise
import java.util
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.BackgroundCallback
import org.apache.curator.framework.api.CuratorEvent
import org.apache.curator.framework.api.CuratorEventType
import org.apache.curator.framework.recipes.cache.ChildData
import org.apache.curator.framework.recipes.cache.TreeCache
import org.apache.zookeeper.KeeperException
import scala.collection.JavaConversions._
import scala.util.Try

final class InstallQueue(client: CuratorFramework) extends
  ProcessorView with ProducerView with ReaderView {
  import InstallQueue._

  private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  private[this] val pendingOperationsPath = "/package/pendingOperations"
  private[this] val failedOperationsPath = "/package/failedOperations"

  private[this] val pendingOperationCache = new TreeCache(client, pendingOperationsPath)
  pendingOperationCache.start()
  private[this] val failedOperationCache = new TreeCache(client, failedOperationsPath)
  failedOperationCache.start()

  /** Returns the next operation in the queue. The order of the
    * elements will be determined by creation time. Earlier created
    * operations will be at the front of the queue.
    *
    * @return the next pending operation
    */
  override def next(): Future[Option[PendingOperation]] = {
    val noFailure = getPendingCoordinates.flatMap { pcs =>
      Future.collect(pcs.map(getPendingOperationWithoutFailure)).map { ops =>
        ops.sortBy(_.creationTime).headOption
      }
    }

    noFailure.flatMap {
      case None => Future.value(None)
      case Some(po) =>
        val failure: Future[Option[OperationFailure]] = getOperationFailure(po.packageCoordinate)
        failure.map(of => Some(po.copy(failure = of)))
    }
  }

  private[this] def getOperationFailure(pc: PackageCoordinate): Future[Option[OperationFailure]] = {
    val promise = Promise[Option[OperationFailure]]
    client.getData.inBackground(
      handler { case event if event.getType == CuratorEventType.GET_DATA =>
        val code = KeeperException.Code.get(event.getResultCode)
        code match {
          case KeeperException.Code.OK =>
            promise.setValue(Some(Envelope.decodeData[OperationFailure](event.getData)))
          case KeeperException.Code.NONODE =>
            promise.setValue(None)
          case _ =>
            promise.setException(KeeperException.create(code, event.getPath))
        }
      }
    ).forPath(
      s"$failedOperationsPath/${pc.as[String]}"
    )
    promise
  }

  private[this] def getPendingOperationWithoutFailure(pc: PackageCoordinate): Future[PendingOperation] = {
    val promise = Promise[PendingOperation]()
    client.getData.inBackground(
      handler { case event if event.getType == CuratorEventType.GET_DATA =>
          val code = KeeperException.Code.get(event.getResultCode)
          code match {
            case KeeperException.Code.OK =>
              promise.setValue(
                PendingOperation(
                  pc,
                  Envelope.decodeData[Operation](event.getData),
                  None,
                  event.getStat.getCtime
                )
              )
            case _ =>
              promise.setException(KeeperException.create(code, event.getPath))
          }
      }
    ).forPath(
      s"$pendingOperationsPath/${pc.as[String]}"
    )
    promise
  }

  private[this] def getPendingCoordinates: Future[List[PackageCoordinate]] = {
    val promise = Promise[List[PackageCoordinate]]()
    client.getChildren.inBackground(
      handler { case event if event.getType == CuratorEventType.CHILDREN =>
        val code = KeeperException.Code.get(event.getResultCode)
        code match {
          case KeeperException.Code.OK =>
            val pcs = event.getChildren.toList.map(_.as[Try[PackageCoordinate]].toOption.get) //TODO
            promise.setValue(pcs)
          case KeeperException.Code.NONODE =>
            promise.setValue(List())
          case _ =>
            promise.setException(KeeperException.create(code, event.getPath))
        }
      }
    ).forPath(
      pendingOperationsPath
    )
    promise
  }

  /** Signals to the install queue that the operation on the package
    * failed. This will "move" the package to the error pile.
    *
    * @param packageCoordinate The package coordinate of the
    *                          package on which the operation failed
    * @param operation         The operation that failed
    * @param failure           The failure that occurred
    * @return a future that will complete when state changes has been completed
    */
  override def failure(
    packageCoordinate: PackageCoordinate,
    operation: Operation,
    failure: ErrorResponse
  ): Future[Unit] = {
    val operationFailure = OperationFailure(operation, failure)
    val operationPath = s"$pendingOperationsPath/${packageCoordinate.as[String]}"
    val failurePath = s"$failedOperationsPath/${packageCoordinate.as[String]}"

    val transaction =
      client.inTransaction()
        .delete().forPath(operationPath)
      .and()
        .create().forPath(failurePath, Envelope.encodeData(operationFailure))
      .and()

    try {
      transaction.commit()
      Future.Unit
    } catch {
      case e: KeeperException.NoNodeException =>
        createFailedOperationsPath().flatMap(_ => this.failure(packageCoordinate, operation, failure))
    }
  }

  private[this] def createFailedOperationsPath(): Future[Unit] = {
    val promise = Promise[Unit]()
    client.create.creatingParentsIfNeeded.inBackground(
      handler { case event if event.getType == CuratorEventType.CREATE =>
        val code = KeeperException.Code.get(event.getResultCode)
        code match {
          case KeeperException.Code.OK =>
            promise.become(Future.Unit)
          case KeeperException.Code.NODEEXISTS =>
            promise.become(Future.Unit)
          case _ =>
            promise.setException(KeeperException.create(code, event.getPath))
        }
      }).forPath(
      failedOperationsPath
    )
    promise
  }

  /** Signals to the install queue that the operation on the package has
    * been successful. This will delete the node from the install queue.
    *
    * @param packageCoordinate The package coordinate of the package whose
    *                          operation has succeeded.
    * @return a future that will complete when state changes have been completed
    */
  override def success(packageCoordinate: PackageCoordinate): Future[Unit] = {
    val promise = Promise[Unit]()
    val operationPath = s"$pendingOperationsPath/${packageCoordinate.as[String]}"
    client.delete().inBackground(
      handler { case event if event.getType == CuratorEventType.DELETE =>
        val code = KeeperException.Code.get(event.getResultCode)
        code match {
          case KeeperException.Code.OK =>
            promise.setValue(())
          case _ =>
            promise.setException(KeeperException.create(code, event.getPath))
        }
      }
    ).forPath(
      operationPath
    )
    promise
  }

  /** Adds an operation on a package to the install queue. It will return
    * an object signaling failure when there is already an operation outstanding
    * on the package coordinate.
    *
    * @param packageCoordinate the package coordinate on which the operation
    *                          should be performed.
    * @param operation         The operation to be performed
    * @return Created if the operation was added to the queue, else AlreadyExists
    *         if the operation was not added to the queue.
    */
  override def add(packageCoordinate: PackageCoordinate, operation: Operation): Future[AddResult] = {
    val promise = Promise[AddResult]()
    val pkgPath = s"$pendingOperationsPath/${packageCoordinate.as[String]}"
    client.create.creatingParentsIfNeeded.inBackground(
        handler { case event if event.getType == CuratorEventType.CREATE =>
          val code = KeeperException.Code.get(event.getResultCode)
          code match {
            case KeeperException.Code.OK =>
              promise.setValue(Created)
            case KeeperException.Code.NODEEXISTS =>
              promise.setValue(AlreadyExists)
            case _ =>
              promise.setException(KeeperException.create(code, event.getPath))
          }
      }).forPath(
      pkgPath,
      Envelope.encodeData(operation)
    )
    promise
  }

  /** Shows the status of every package in the install queue.
    *
    * @return A map from package coordinate to the state of any pending or
    *         failed operations associated with that package coordinate
    */
  override def viewStatus(): Future[Map[PackageCoordinate, OperationStatus]] = {
    val pending =
      Option(pendingOperationCache
        .getCurrentChildren(pendingOperationsPath))
        .getOrElse(new util.HashMap[String, ChildData]())
        .toMap
        .map { case (encodedPc, data: ChildData) =>
          val pc = encodedPc.as[Try[PackageCoordinate]].toOption.get //TODO
          logger.debug("encoded package coordinate: {}", encodedPc)
          logger.debug("decoded package coordinate: {}", pc)
          pc -> Some(Envelope.decodeData[Operation](data.getData))}
        .withDefault(_ => None)

    val failed =
      Option(failedOperationCache
        .getCurrentChildren(failedOperationsPath))
        .getOrElse(new util.HashMap[String, ChildData]())
        .toMap
        .map { case (encodedPc, data) =>
          val pc = encodedPc.as[Try[PackageCoordinate]].toOption.get //TODO
          logger.debug("encoded package coordinate: {}", encodedPc)
          logger.debug("decoded package coordinate: {}", pc)
          pc -> Some(Envelope.decodeData[OperationFailure](data.getData))}
        .withDefault(_ => None)

    Future.value((failed.keySet ++ pending.keySet)
      .map(pc => pc -> OperationStatus(pending(pc), failed(pc))).toMap)
  }
}

object InstallQueue {
  def apply(client: CuratorFramework): InstallQueue = new InstallQueue(client)

  private def handler(handle: PartialFunction[CuratorEvent, Unit]) = {
    new BackgroundCallback {
      override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
        handle(event)
      }
    }
  }
}

