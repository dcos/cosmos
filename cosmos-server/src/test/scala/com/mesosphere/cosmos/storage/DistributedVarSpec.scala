package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.test.TestUtil._
import com.twitter.util.Await
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.scalatest.FreeSpec

/**
  * Requires zookeeper to be running on localhost:2181
  * ignoring tests for now
  */
class DistributedVarSpec extends FreeSpec {

  import DistributedVarSpec._

  private[this] def startZk(): CuratorFramework = {
    val zk = CuratorFrameworkFactory.newClient(
      zkPath,
      new ExponentialBackoffRetry(1000, 300)
    )
    zk.start()
    zk.getZookeeperClient.blockUntilConnectedOrTimedOut()
    zk
  }

  "When variable path does not exist default value should be written and returned" ignore {
    val zk = startZk()
    val path = "/unset"
    val dvar = DistributedVar(zk, path, default)
    val exp = default
    val act = Await.result(dvar.get)
    assertResult(exp)(act)
    zk.delete().forPath(path)
    ()
  }

  "When variable path does exist the value should be decoded and returned" ignore {
    val zk = startZk()
    val path = "/exists"
    val exp = TestClass("foo")
    zk.create().forPath(path, Envelope.encodeData(exp))
    val dvar = DistributedVar(zk, path, default)
    val act = Await.result(dvar.get)
    assertResult(exp)(act)
    zk.delete().forPath(path)
    ()
  }

  "Multiple variables can share the same path, i.e. be aliased to the same value" ignore {
    val zk = startZk()
    val path = "/exists"
    val exp = TestClass("foo")
    val v = DistributedVar(zk, path, default)
    val v2 = DistributedVar(zk, path, default)
    val act = Await.result(v.set(exp).flatMap(_ => v2.get))
    assertResult(exp)(act)
    zk.delete().forPath(path)
    ()
  }

  "A write of a value X followed by a read should return X given that no other" +
    " writes have occurred" ignore {
    val zk = startZk()
    val path = "/readOwnWrites"
    val dvar = DistributedVar(zk, path, default)
    val exp = TestClass("foo")
    val act = Await.result(dvar.set(exp).flatMap(_ => dvar.get))
    assertResult(exp)(act)
    zk.delete().forPath(path)
    ()
  }
}

object DistributedVarSpec {
  private val zkPath = "localhost:2181"
  private val default = TestClass("")
}
