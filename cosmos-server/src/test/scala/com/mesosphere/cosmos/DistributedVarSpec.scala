package com.mesosphere.cosmos

import com.mesosphere.cosmos.storage.DistributedVar
import com.netaporter.uri.Uri
import com.twitter.util.Await
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.scalatest.FreeSpec

/**
  * @requires zookeeper to be running on localhost:2181
  */
class DistributedVarSpec extends FreeSpec {
  val zkUri = "localhost:2181"
  val zk = CuratorFrameworkFactory.newClient(
    zkUri,
    new ExponentialBackoffRetry(1000, Integer.MAX_VALUE)
  )
  zk.start()
  zk.getZookeeperClient.blockUntilConnectedOrTimedOut()

  "An unset variable should get the empty string" in {
    val path = "/unset"
    val dvar = DistributedVar(zk, Uri.parse(path))
    val exp = ""
    val act = Await.result(dvar.flatMap(_.get))
    assertResult(exp)(act)
    zk.delete().forPath(path)
    ()
  }

  "The user should be able to create a variable with a path that already exists in zookeeper" in {
    val path = "/exists"
    val exp = "foo"
    zk.create().forPath(path, exp.getBytes())
    val dvar = DistributedVar(zk, Uri.parse(path))
    val act = Await.result {
      for {
        v <- dvar
        r <- v.get
      } yield r
    }
    assertResult(exp)(act)
    zk.delete().forPath(path)
    ()
  }

  "The user should be able to create multiple variables with the same path" in {
    val path = "/exists"
    val exp = "foo"
    val act = Await.result {
      for {
        v <- DistributedVar(zk, Uri.parse(path))
        _ <- v.set(exp)
        v2 <- DistributedVar(zk, Uri.parse(path))
        r <- v2.get
      } yield r
    }
    assertResult(exp)(act)
    zk.delete().forPath(path)
    ()
  }

  "The user should be able to read their own writes" in {
    val path = "/readOwnWrites"
    val dvar = DistributedVar(zk, Uri.parse(path))
    val exp = "foo"
    val act = Await.result {
      for {
        v <- dvar
        _ <- v.set(exp)
        r <- v.get
      } yield r
    }
    assertResult(exp)(act)
    zk.delete().forPath(path)
    ()
  }
}
