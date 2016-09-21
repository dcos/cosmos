package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.circe.{MediaTypedDecoder, MediaTypedEncoder}
import com.twitter.util.{Future, Promise}
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.{BackgroundCallback, CuratorEvent, CuratorEventType}
import org.apache.zookeeper.KeeperException

final class DistributedVar[T] private(
  client: CuratorFramework, path: String, default: T
)(implicit mediaTypedEncoder: MediaTypedEncoder[T], mediaTypedDecoder: MediaTypedDecoder[T]) {
  import DistributedVar._

  private def getOption: Future[Option[T]] = {
    val promise = Promise[Option[T]]()
    client.getData.inBackground(handler { case event: CuratorEvent if event.getType == CuratorEventType.GET_DATA =>
      KeeperException.Code.get(event.getResultCode) match {
        case KeeperException.Code.OK => promise.setValue(Some(Envelope.decodeData(event.getData)))
        case KeeperException.Code.NONODE => promise.setValue(None)
        case code => promise.setException(KeeperException.create(code, event.getPath))
      }
    }).forPath(path)
    promise
  }

  private def setOption(value: T): Future[Option[T]] = {
    val promise = Promise[Option[T]]()
    client.setData().inBackground(handler { case event: CuratorEvent if event.getType == CuratorEventType.SET_DATA =>
      KeeperException.Code.get(event.getResultCode) match {
        case KeeperException.Code.OK => promise.setValue(Some(value))
        case KeeperException.Code.NONODE => promise.setValue(None)
        case code => promise.setException(KeeperException.create(code, event.getPath))
      }
    }).forPath(path, Envelope.encodeData(value))
    promise
  }

  private def create(value: T, message: String = "")(ifNodeExists: => Future[T]): Future[T] = {
    val promise = Promise[T]()
    client.create().inBackground(handler { case event: CuratorEvent if event.getType == CuratorEventType.CREATE =>
      KeeperException.Code.get(event.getResultCode) match {
        case KeeperException.Code.OK => promise.setValue(value)
        case KeeperException.Code.NODEEXISTS => promise.become(ifNodeExists)
        case code => promise.setException(KeeperException.create(code, event.getPath))
      }
    }).forPath(path, Envelope.encodeData(value))
    promise
  }

  def get: Future[T] = {
    getOption.flatMap{ valueOption =>
      valueOption.map(Future.value)
        .getOrElse(create(default)(ifNodeExists = get))
    }
  }

  def set(value: T): Future[T] = {
    setOption(value).flatMap { valueOption =>
      valueOption.map(Future.value)
        .getOrElse(create(value, "set")(ifNodeExists = set(value)))
    }
  }
}

object DistributedVar {
  def apply[T](
    client: CuratorFramework, path: String, default: T
  )( implicit mediaTypedEncoder: MediaTypedEncoder[T],
     mediaTypedDecoder: MediaTypedDecoder[T] )
  : DistributedVar[T] = {
    new DistributedVar(client, path, default)
  }

  private def handler(handle: PartialFunction[CuratorEvent, Unit]) = {
    new BackgroundCallback {
      override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
        handle(event)
      }
    }
  }
}
