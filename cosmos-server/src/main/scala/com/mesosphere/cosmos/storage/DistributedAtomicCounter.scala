package com.mesosphere.cosmos.storage

import com.twitter.util.Future

import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong
import org.apache.curator.retry.ExponentialBackoffRetry

import com.mesosphere.cosmos.ZooKeeperStorageError

private[cosmos] final class DistributedAtomicCounter(
  zkClient: CuratorFramework
) {

  private[this] val counterPath: String = "/package/counter"
  private[this] val retryPolicy = new ExponentialBackoffRetry(1000, 3)
  private[this] val counter = new DistributedAtomicLong(zkClient, counterPath, retryPolicy)

  def incrementAndGet: Future[Long] = {
    Future {
      val newValue = counter.increment()
      if (newValue.succeeded()) {
        newValue.postValue()
      } else {
        throw new ZooKeeperStorageError("Could not acquire new counter value")
      }
    }
  }
}
