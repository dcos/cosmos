package com.mesosphere.cosmos.storage

import com.twitter.finagle.stats.{NullStatsReceiver, Stat, StatsReceiver}
import com.twitter.util.Future
import com.twitter.util.FuturePool

import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong
import org.apache.curator.retry.ExponentialBackoffRetry

import com.mesosphere.cosmos.ZooKeeperStorageError

private[cosmos] final class DistributedAtomicCounter(
  zkClient: CuratorFramework,
  pool: FuturePool
)(implicit
  statsReceiver: StatsReceiver = NullStatsReceiver
) {

  import DistributedAtomicCounter._

  private[this] val counter = new DistributedAtomicLong(zkClient, counterPath, retryPolicy)

  private[this] val stats = statsReceiver.scope("distributedCounter")

  def incrementAndGet: Future[Long] = {
    Stat.timeFuture(stats.stat("call")) {
      pool {
        val newValue = counter.increment()

        val newStats = newValue.getStats()
        stats.stat("optimisticTries").add(newStats.getOptimisticTries().asInstanceOf[Float])
        stats.stat("optimisticTriesMs").add(newStats.getOptimisticTimeMs().asInstanceOf[Float])

        if (newValue.succeeded()) {
          newValue.postValue()
        } else {
          throw new ZooKeeperStorageError("Could not acquire new counter value")
        }
      }
    }
  }
}

private object DistributedAtomicCounter {

  private val counterPath: String = "/package/counter"
  private val retryPolicy = new ExponentialBackoffRetry(1000, 3)

}
