package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.ConcurrentAccess
import com.mesosphere.cosmos.test.TestUtil._
import com.twitter.util.Await
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.scalatest.FreeSpec

/**
  * Requires zookeeper to be running on localhost:2181
  * ignoring tests for now
  */
class DistributedGlobalSpec extends FreeSpec {

  import DistributedGlobalSpec._

  private[this] def startZk(): CuratorFramework = {
    val zk = CuratorFrameworkFactory.newClient(
      zkPath,
      new ExponentialBackoffRetry(1000, 300)
    )
    zk.start()
    zk.getZookeeperClient.blockUntilConnectedOrTimedOut()
    zk
  }

  "When path does not exist default value should be written and returned" ignore {
    val zk = startZk()
    val path = "/unset"
    val global = DistributedGlobal(zk, path, default)
    val exp = default
    val act = Await.result(global.get)._1
    assertResult(exp)(act)
    zk.delete().forPath(path)
    ()
  }

  "When path does exist the value should be decoded and returned" ignore {
    val zk = startZk()
    val path = "/exists"
    val exp = TestClass("foo")
    zk.create().forPath(path, Envelope.encodeData(exp))
    val global = DistributedGlobal(zk, path, default)
    val act = Await.result(global.get)._1
    assertResult(exp)(act)
    zk.delete().forPath(path)
    ()
  }

  "A write of a value X followed by a read should return X given that no other" +
    " writes have occurred" ignore {
    val zk = startZk()
    val path = "/readOwnWrites"
    val global = DistributedGlobal(zk, path, default)
    val exp = TestClass("foo")
    val act = Await.result(global.set(exp).flatMap(_ => global.get))._1
    assertResult(exp)(act)
    zk.delete().forPath(path)
    ()
  }

  "A write of a value X followed by a cached read should return X or any other" +
    " value written before X including the default" ignore {
    val zk = startZk()
    val path = "/cacheDoesNotReadOwnWrites"
    val global = DistributedGlobal(zk, path, default)
    val x = TestClass("x")
    val y = TestClass("y")
    val act = Await.result(
      global.set(x)
        .flatMap(_ => global.set(y))
        .flatMap(_ => global.getCached)
    )._1
    assert(Set(default, x, y).contains(act))
    zk.delete().forPath(path)
    ()
  }

  "A series of set with version operations should succeed if there are no" +
    " other concurrent sets" ignore {
    val zk = startZk()
    val path = "/setWithVersion"
    val global = DistributedGlobal(zk, path, default)
    val max = 10
    val values = (0 to max).map(v => TestClass(v.toString))
    val exp = TestClass(max.toString)
    val act = Await.result(
      values.foldLeft(global.get){
        case (acc, value) => acc.flatMap(p => global.set(value, p._2))
      }
    )._1
    assertResult(exp)(act)
    zk.delete().forPath(path)
    ()
  }

  "A series of set with version operations should fail if there are" +
    " other concurrent sets" ignore {
    val zk = startZk()
    val path = "/setWithVersion"
    val global = DistributedGlobal(zk, path, default)
    val max = 10
    val values = (0 to max).map(v => TestClass(v.toString))
    val exp = TestClass(max.toString)
    val act = Await.result(
      values.foldLeft(global.get){
        case (acc, value) => acc.flatMap(p => global.set(value, p._2))
      }
    )._1
    intercept[ConcurrentAccess]{
      Await.result(global.set(TestClass("fail!!"), max))
    }
    assertResult(exp)(act)
    zk.delete().forPath(path)
    ()
  }

}

object DistributedGlobalSpec {
  private val zkPath = "localhost:2181"
  private val default = TestClass("")
}
