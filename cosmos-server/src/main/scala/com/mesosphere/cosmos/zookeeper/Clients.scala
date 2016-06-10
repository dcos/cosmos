package com.mesosphere.cosmos.zookeeper

import java.nio.charset.StandardCharsets

import scala.annotation.tailrec
import scala.collection.JavaConverters._

import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.api.ACLProvider
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.data.Id

import com.mesosphere.cosmos.zookeeperUri
import com.mesosphere.cosmos.model.ZooKeeperUri

object Clients {
  val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def createAndInitialize(
    zkUri: ZooKeeperUri,
    zkCredentials: Option[(String, String)]
  ): CuratorFramework = {
    val zkClientBuilder = CuratorFrameworkFactory
      .builder()
      .namespace(zkUri.path.stripPrefix("/"))
      .connectString(zkUri.connectString)
      .retryPolicy(new ExponentialBackoffRetry(1000, 3))

    val authInfo = zkCredentials.map {
      case (user, secret) =>
        (
          s"$user:$secret".getBytes(StandardCharsets.UTF_8),
          CosmosAclProvider(user, secret)
        )
    }

    authInfo.foreach {
      case (authBytes, aclProvider) =>
        logger.info("Enabling authorization and ACL provider for ZooKeeper client")
        zkClientBuilder
          .authorization("digest", authBytes)
          .aclProvider(aclProvider)
    }

    val zkClient = zkClientBuilder.build

    // Start the client
    zkClient.start()

    authInfo.foreach {
      case (_, aclProvider) =>
        updateAcls(zkClient, aclProvider)
    }

    zkClient
  }

  private[this] def updateAcls(
    zkClient: CuratorFramework,
    aclProvider: ACLProvider
  ): Unit = {
    updateAcls(
      zkClient,
      aclProvider,
      zkClient.getChildren.forPath("/").asScala.toList.map("/" + _)
    )
  }

  @tailrec
  private[this] def updateAcls(
    zkClient: CuratorFramework,
    aclProvider: ACLProvider,
    paths: List[String]
  ): Unit = {
    paths match {
      case path :: restOfPaths =>
        logger.info("Updating ZNode ACL during initialization: {}", path)
        zkClient
          .setACL()
          .withACL(aclProvider.getAclForPath(path))
          .forPath(path)

        val childrenPaths = zkClient.getChildren.forPath(
          path
        ).asScala.toList.map { child =>
          path + "/" + child
        }

        updateAcls(zkClient, aclProvider, childrenPaths ++ restOfPaths)
      case Nil =>
        // No paths left. Nothing to do.
    }
  }
}
