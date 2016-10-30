package com.mesosphere.cosmos.repository

import com.twitter.util.Await
import com.twitter.util.Future
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.leader.CancelLeadershipException
import org.apache.curator.framework.recipes.leader.LeaderSelector
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener
import org.apache.curator.framework.state.ConnectionState

final class SyncFutureLeader private (
  curatorClient: CuratorFramework,
  processor: () => Future[Unit]
) extends LeaderSelectorListener with AutoCloseable {
  private[this] val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  private[this] val leaderSelector = new LeaderSelector(
    curatorClient,
    "/processor/leader",
    this
  )

  override def stateChanged(
    client: CuratorFramework,
    newState: ConnectionState
  ): Unit = {
    if (newState == ConnectionState.SUSPENDED || newState == ConnectionState.LOST) {
      /* The LeaderSelector recipe will catch this type of exception and attempt
       * to interrupt the thread executing the takeLeadership method below.
       */
      throw new CancelLeadershipException()
    }
  }

  override def takeLeadership(client: CuratorFramework): Unit = {
    try {
      Await.result {
        /* NOTE: To reduce the probability of two different Cosmos performing
         * the same operation, it is very important that this function doesn't
         * block forever. The return Future should try to terminate as soon as
         * possible.
         */
        processor().onFailure { error =>
          logger.error(s"Got a failure from the processor", error)
        }
      }
    } catch {
      case cancel: InterruptedException =>
        // Just log that we lost leadership
        logger.warn("Lost leadership while processing operation...", cancel)
    }
  }

  def start(): Unit = {
    leaderSelector.autoRequeue()
    leaderSelector.start()
  }

  override def close(): Unit = {
    leaderSelector.close()
  }
}

object SyncFutureLeader {
  def apply(
    curatorClient: CuratorFramework,
    processor: () => Future[Unit]
  ): SyncFutureLeader = {
    new SyncFutureLeader(curatorClient, processor)
  }
}
