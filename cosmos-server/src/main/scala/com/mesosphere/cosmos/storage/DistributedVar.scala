package com.mesosphere.cosmos.storage

import com.netaporter.uri.Uri
import com.twitter.util.{Future, Promise}
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.{BackgroundCallback, CuratorEvent, CuratorEventType}
import org.apache.zookeeper.KeeperException

class DistributedVar private(zkClient: CuratorFramework, path: Uri) {
  import DistributedVar._

  def get: Future[String] = {
    val promise = Promise[String]()
    zkClient.getData.inBackground(
      promiseHandler(
        promise,
        CuratorEventType.GET_DATA
      )(event => new String(event.getData))
    ).forPath(path.toString())
    promise
  }

  def set(value: String): Future[String] = {
    val promise = Promise[String]()
    zkClient.setData().inBackground(
      promiseHandler(
        promise,
        CuratorEventType.SET_DATA
      )(_ => value)
    ).forPath(path.toString(), value.getBytes)
    promise
  }
}

object DistributedVar {
  def apply(zkClient: CuratorFramework, path: Uri): Future[DistributedVar] = {
    val promise = Promise[DistributedVar]()
    zkClient.create().inBackground(
      promiseHandler(
        promise,
        CuratorEventType.CREATE,
        Set(KeeperException.Code.OK, KeeperException.Code.NODEEXISTS)
      )(_ => new DistributedVar(zkClient, path))
    ).forPath(path.toString(), "".getBytes())
    promise
  }

  private def promiseHandler[T](
    promise: Promise[T],
    eventType: CuratorEventType,
    accepted: Set[KeeperException.Code] = Set(KeeperException.Code.OK)
  )(value: (CuratorEvent) => T): BackgroundCallback = {
    new BackgroundCallback {
      override def processResult(client: CuratorFramework, event: CuratorEvent): Unit = {
        if (event.getType == eventType) {
          val code = KeeperException.Code.get(event.getResultCode)
          if (accepted.contains(code)) {
            promise.setValue(value(event))
          } else {
            promise.setException(KeeperException.create(code, event.getPath))
          }
        }
      }
    }
  }
}

