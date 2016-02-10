package com.mesosphere.cosmos

import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.test.TestingCluster
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.util.Random

trait ZooKeeperFixture extends BeforeAndAfterAll { this: Suite =>

  var zkCluster: TestingCluster = _

  override def beforeAll(): Unit = {
    zkCluster = new TestingCluster(3)
    zkCluster.start()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    try { super.afterAll() }
    finally { Option(zkCluster).foreach(_.close()) }
  }

  protected[this] final def withZooKeeperClient(testFn: CuratorFramework => Unit): Unit = {
    val connectString = zkCluster.getConnectString
    val retryPolicy = new ExponentialBackoffRetry(1000, 3)
    val client = CuratorFrameworkFactory.builder()
      .namespace(Random.alphanumeric.take(10).mkString)
      .connectString(connectString)
      .retryPolicy(retryPolicy)
      .build
    client.start()

    try {
      testFn(client)
      val _ = client.delete.deletingChildrenIfNeeded.forPath("/")
    } finally {
      client.close()
    }
  }

}
