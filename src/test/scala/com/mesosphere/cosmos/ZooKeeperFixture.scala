package com.mesosphere.cosmos

import java.util.concurrent.TimeUnit

import com.mesosphere.cosmos.model.ZooKeeperUri
import com.twitter.app.Flags
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.RetryNTimes
import org.apache.curator.test.TestingCluster
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.util.Random

trait ZooKeeperFixture extends BeforeAndAfterAll { this: Suite =>
  private[this] val logger = org.slf4j.LoggerFactory.getLogger(classOf[ZooKeeperFixture])

  var zkCluster: TestingCluster = _
  var zkUri: ZooKeeperUri = _

  override def beforeAll(): Unit = {
    zkCluster = new TestingCluster(1)
    zkCluster.start()
    val connectString = zkCluster.getConnectString
    val baseZNode = Random.alphanumeric.take(10).mkString
    zkUri = ZooKeeperUri.parse(s"zk://$connectString/$baseZNode").get()
    logger.info("Setting com.mesosphere.cosmos.zookeeperUri={}", zkUri.toString)
    System.setProperty("com.mesosphere.cosmos.zookeeperUri", zkUri.toString)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    try { super.afterAll() }
    finally { Option(zkCluster).foreach(_.close()) }
  }

  protected[this] final def withZooKeeperClient(testFn: CuratorFramework => Unit): Unit = {
    val retryPolicy = new RetryNTimes(3, 500)
    logger.info("Connecting to zk: {}", zkUri.toString)
    val client = CuratorFrameworkFactory.builder()
      .namespace(zkUri.path.stripPrefix("/"))
      .connectString(zkUri.connectString)
      .retryPolicy(retryPolicy)
      .connectionTimeoutMs(500)
      .build
    client.start()
    assert(client.blockUntilConnected(1000, TimeUnit.MILLISECONDS))

    try {
      testFn(client)
      val _ = client.delete.deletingChildrenIfNeeded.forPath("/")
    } finally {
      client.close()
    }
  }

}
