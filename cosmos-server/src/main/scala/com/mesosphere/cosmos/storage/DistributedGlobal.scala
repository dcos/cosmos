package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.ConcurrentAccess
import com.mesosphere.cosmos.circe.{MediaTypedDecoder, MediaTypedEncoder}
import com.twitter.util.{Future, Promise}
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.{BackgroundCallback, CuratorEvent, CuratorEventType}
import org.apache.curator.framework.recipes.cache.NodeCache
import org.apache.zookeeper.KeeperException

final class DistributedGlobal[T] private(
  client: CuratorFramework, path: String, default: T
)(implicit mediaTypedEncoder: MediaTypedEncoder[T], mediaTypedDecoder: MediaTypedDecoder[T]) {
  import DistributedGlobal._

  private[this] val cache = new NodeCache(client, path)
  cache.start()

  private def getCachedOption: Future[Option[(T, Int)]] = {
    Future {
      Option(cache.getCurrentData).map { response =>
        (Envelope.decodeData(response.getData), response.getStat.getVersion)
      }
    }
  }

  private def getOption: Future[Option[(T, Int)]] = {
    val promise = Promise[Option[(T, Int)]]()
    client.getData.inBackground(handler { case event: CuratorEvent if event.getType == CuratorEventType.GET_DATA =>
      KeeperException.Code.get(event.getResultCode) match {
        case KeeperException.Code.OK =>
          promise.setValue(Some((Envelope.decodeData(event.getData), event.getStat.getVersion)))
        case KeeperException.Code.NONODE =>
          promise.setValue(None)
        case code =>
          promise.setException(KeeperException.create(code, event.getPath))
      }
    }).forPath(path)
    promise
  }

  private def setOption(value: T): Future[Option[(T, Int)]] = {
    val promise = Promise[Option[(T, Int)]]()
    client.setData().inBackground(handler { case event: CuratorEvent if event.getType == CuratorEventType.SET_DATA =>
      KeeperException.Code.get(event.getResultCode) match {
        case KeeperException.Code.OK =>
          promise.setValue(Some((value, event.getStat.getVersion)))
        case KeeperException.Code.NONODE =>
          promise.setValue(None)
        case code =>
          promise.setException(KeeperException.create(code, event.getPath))
      }
    }).forPath(path, Envelope.encodeData(value))
    promise
  }

  private def setOption(value: T, version: Int): Future[Option[(T, Int)]] = {
    val promise = Promise[Option[(T, Int)]]()
    client.setData().withVersion(version)
      .inBackground(handler { case event: CuratorEvent if event.getType == CuratorEventType.SET_DATA =>
        KeeperException.Code.get(event.getResultCode) match {
          case KeeperException.Code.OK =>
            promise.setValue(Some((value, event.getStat.getVersion)))
          case KeeperException.Code.NONODE =>
            promise.setValue(None)
          case code if code == KeeperException.Code.BADVERSION =>
            promise.setException(ConcurrentAccess(KeeperException.create(code, event.getPath)))
          case code =>
            promise.setException(KeeperException.create(code, event.getPath))
        }
      }).forPath(path, Envelope.encodeData(value))
    promise
  }

  private def create(value: T)(ifNodeExists: => Future[(T, Int)]): Future[(T, Int)] = {
    val promise = Promise[(T, Int)]()
    client.create().inBackground(handler { case event: CuratorEvent if event.getType == CuratorEventType.CREATE =>
      KeeperException.Code.get(event.getResultCode) match {
        case KeeperException.Code.OK =>
          promise.setValue((value, 0)) // assumes newly created nodes have version 0
        case KeeperException.Code.NODEEXISTS =>
          promise.become(ifNodeExists)
        case code =>
          promise.setException(KeeperException.create(code, event.getPath))
      }
    }).forPath(path, Envelope.encodeData(value))
    promise
  }

  def get: Future[(T, Int)] = {
    getOption.flatMap{ valueOption =>
      valueOption.map(Future.value)
        .getOrElse(create(default)(ifNodeExists = get))
    }
  }

  def set(value: T): Future[(T, Int)] = {
    setOption(value).flatMap { valueOption =>
      valueOption.map(Future.value)
        .getOrElse(create(value)(ifNodeExists = set(value)))
    }
  }

  def set(value: T, version: Int): Future[(T, Int)] = {
    setOption(value, version).flatMap { valueOption =>
      valueOption.map(Future.value)
        .getOrElse(create(value)(ifNodeExists = set(value, version)))
    }
  }

  def getCached: Future[(T, Int)] = {
    getCachedOption.flatMap {
      case Some((stat, data)) => Future((stat, data))
      case None => get
    }
  }
}

object DistributedGlobal {

  def apply[T](
    client: CuratorFramework, path: String, default: T
  )( implicit
     mediaTypedEncoder: MediaTypedEncoder[T],
     mediaTypedDecoder: MediaTypedDecoder[T]): DistributedGlobal[T] = {
    new DistributedGlobal(client, path, default)
  }

  private def handler(handle: PartialFunction[CuratorEvent, Unit]) = {
    new BackgroundCallback {
      override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
        handle(event)
      }
    }
  }

}
