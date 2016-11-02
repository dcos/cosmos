package com.mesosphere.cosmos.storage.installqueue

import com.mesosphere.cosmos.InstallQueueError
import com.mesosphere.cosmos.converter.Common.packageCoordinateToBase64String
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.rpc.v1.model.PackageCoordinate
import com.mesosphere.cosmos.storage.Envelope
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedDecoders._
import com.mesosphere.cosmos.storage.v1.circe.MediaTypedEncoders._
import com.twitter.bijection.Conversion.asMethod
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
import scala.collection.mutable.ListBuffer

final class InstallQueue private(client: CuratorFramework) extends
  ProcessorView with ProducerView with ReaderView {

  import InstallQueue._

  private[this] val operationStatusCache = new TreeCache(client, installQueuePath)
  operationStatusCache.start()

  override def next(): Future[Option[PendingOperation]] = {
    getAllCoordinates.flatMap { coordinates =>
      Future.collect(coordinates.map(getOperationIfPending)).map { operationsMaybePending =>
        val pendingOperations = operationsMaybePending.flatten
        if (pendingOperations.isEmpty) {
          None
        } else {
          Some(pendingOperations.minBy(_.info.getCtime).value)
        }
      }
    }
  }

  private[this] def getOperationIfPending
  (
    packageCoordinate: PackageCoordinate
  ): Future[Option[Info[PendingOperation]]] = {
    getOperationStatus(packageCoordinate).map { maybeOperationStatus =>
      maybeOperationStatus.flatMap {
        case Info(info, Pending(operation, failure)) =>
          Some(Info(
            info,
            PendingOperation(packageCoordinate, operation, failure)
          ))
        case _ => None
      }
    }
  }

  private[this] def getAllCoordinates: Future[List[PackageCoordinate]] = {
    val promise = Promise[List[PackageCoordinate]]()
    client.getChildren.inBackground(
      handler(promise) { case event if event.getType == CuratorEventType.CHILDREN =>
        val code = KeeperException.Code.get(event.getResultCode)
        code match {
          case KeeperException.Code.OK =>
            Try(event.getChildren.toList.map(_.as[scala.util.Try[PackageCoordinate]].get))
          case KeeperException.Code.NONODE =>
            Return(List())
          case _ =>
            Throw(KeeperException.create(code, event.getPath))
        }
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
    getOperationStatus(packageCoordinate).flatMap {
      case None =>
        val message =
          "Attempted to signal failure on an " +
            s"operation not in the install queue: $packageCoordinate"
        Future.exception(InstallQueueError(message))
      case Some(Info(_, Failed(_))) =>
        val message =
          "Attempted to signal failure on an " +
            s"operation that has failed: $packageCoordinate"
        Future.exception(InstallQueueError(message))
      case Some(Info(info, Pending(operation, _))) =>
        setOperationStatus(
          packageCoordinate,
          Failed(OperationFailure(operation, error)),
          info.getVersion
        )
    }
  }

  private[this] def getOperationStatus
  (
    packageCoordinate: PackageCoordinate
  ): Future[Option[Info[OperationStatus]]] = {
    val promise = Promise[Option[Info[OperationStatus]]]()
    client.getData.inBackground(
      handler(promise) { case event if event.getType == CuratorEventType.GET_DATA =>
        val code = KeeperException.Code.get(event.getResultCode)
        code match {
          case KeeperException.Code.OK =>
            Return(Some(Info(
              event.getStat,
              Envelope.decodeData[OperationStatus](event.getData)
            )))
          case KeeperException.Code.NONODE =>
            Return(None)
          case _ =>
            Throw(KeeperException.create(code, event.getPath))
        }
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
    client.setData().withVersion(version).inBackground(
      handler(promise) { case event if event.getType == CuratorEventType.SET_DATA =>
        val code = KeeperException.Code.get(event.getResultCode)
        code match {
          case KeeperException.Code.OK =>
            Return(())
          case _ =>
            Throw(KeeperException.create(code, event.getPath))
        }
      }
    ).forPath(
      statusPath(packageCoordinate),
      Envelope.encodeData(operationStatus)
    )
    promise
  }

  override def success
  (
    packageCoordinate: PackageCoordinate
  ): Future[Unit] = {
    getOperationStatus(packageCoordinate).flatMap {
      case None =>
        val message =
          "Attempted to signal success on an " +
            s"operation not in the install queue: $packageCoordinate"
        Future.exception(InstallQueueError(message))
      case Some(Info(_, Failed(_))) =>
        val message =
          "Attempted to signal success on an " +
            s"operation that has failed: $packageCoordinate"
        Future.exception(InstallQueueError(message))
      case Some(Info(info, Pending(operation, _))) =>
        deleteOperationStatus(packageCoordinate, info.getVersion)
    }
  }

  private[this] def deleteOperationStatus
  (
    packageCoordinate: PackageCoordinate,
    version: Int
  ): Future[Unit] = {
    val promise = Promise[Unit]()
    client.delete().withVersion(version).inBackground(
      handler(promise) { case event if event.getType == CuratorEventType.DELETE =>
        val code = KeeperException.Code.get(event.getResultCode)
        code match {
          case KeeperException.Code.OK =>
            Return(())
          case _ =>
            Throw(KeeperException.create(code, event.getPath))
        }
      }
    ).forPath(
      statusPath(packageCoordinate)
    )
    promise
  }

  override def add
  (
    packageCoordinate: PackageCoordinate,
    operation: Operation
  ): Future[AddResult] = {
    createOperationStatus(
      packageCoordinate,
      Pending(operation, None)
    ).rescue {
      case e: KeeperException.NodeExistsException =>
        setOperationInOperationStatus(packageCoordinate, operation)
    }
  }

  private[this] def createOperationStatus
  (
    packageCoordinate: PackageCoordinate,
    operationStatus: OperationStatus
  ): Future[AddResult] = {
    val promise = Promise[AddResult]()
    client.create().creatingParentsIfNeeded().inBackground(
      handler(promise) { case event if event.getType == CuratorEventType.CREATE =>
        val code = KeeperException.Code.get(event.getResultCode)
        code match {
          case KeeperException.Code.OK =>
            Return(Created)
          case _ =>
            Throw(KeeperException.create(code, event.getPath))
        }
      }
    ).forPath(
      statusPath(packageCoordinate),
      Envelope.encodeData(operationStatus)
    )
    promise
  }

  private[this] def setOperationInOperationStatus
  (
    packageCoordinate: PackageCoordinate,
    operation: Operation
  ): Future[AddResult] = {
    getOperationStatus(packageCoordinate).flatMap {
      case None =>
        // if we reach this statement, that means there was
        // an operation pending when the user made the request. The operation has
        // since completed, but it is not incorrect to return AlreadyExists, because
        // the request overlaps the existence of a pending operation.
        Future.value(AlreadyExists)
      case Some(Info(_, Pending(_, _))) =>
        Future.value(AlreadyExists)
      case Some(Info(info, Failed(failure))) =>
        setOperationStatus(
          packageCoordinate,
          Pending(operation, Some(failure)),
          info.getVersion
        ).before(Future.value(Created))
    }
  }

  override def viewStatus(): Future[Map[PackageCoordinate, OperationStatus]] = {
    def listTryToTryList[A](listTry: List[Try[A]]): Try[List[A]] = {
      listTry
        .foldLeft(
          Try(ListBuffer[A]())
        ) { (tryOfList, oneTry) =>
          tryOfList.flatMap { list =>
            oneTry.map { element =>
              list :+ element
            }
          }
        }.map(_.toList)
    }

    val operationStatus =
      Option(operationStatusCache
        .getCurrentChildren(installQueuePath))
        .getOrElse(new java.util.HashMap[String, ChildData]())
        .toMap
        .map { case (encodedPackageCoordinate, childData) =>
          Try(encodedPackageCoordinate.as[scala.util.Try[PackageCoordinate]].get).flatMap {
            packageCoordinate =>
              Try(packageCoordinate
                -> Envelope.decodeData[OperationStatus](childData.getData))
          }
        }
        .toList
    Future.const(listTryToTryList(operationStatus).map(_.toMap))
  }

}

object InstallQueue {
  val installQueuePath = "/packages"

  def statusPath(packageCoordinate: PackageCoordinate): String = {
    s"$installQueuePath/${packageCoordinate.as[String]}"
  }

  def apply(client: CuratorFramework): InstallQueue = new InstallQueue(client)

  private case class Info[A](info: Stat, value: A)

  private def handler[A](promise: Promise[A])(handle: PartialFunction[CuratorEvent, Try[A]]) = {
    new BackgroundCallback {
      override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
        promise.update(handle(event))
      }
    }
  }

}


