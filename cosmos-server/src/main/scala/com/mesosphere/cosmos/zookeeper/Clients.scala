package com.mesosphere.cosmos.zookeeper

import com.mesosphere.cosmos.model.ZooKeeperUri
import java.nio.charset.StandardCharsets
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.framework.api.ACLProvider
import org.apache.curator.retry.ExponentialBackoffRetry
import org.slf4j.Logger
import scala.annotation.tailrec
import scala.collection.JavaConverters._

object Clients {
  val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  val retries = 3
  val baseSleepTimeMs = 1000

  def createAndInitialize(zkUri: ZooKeeperUri): CuratorFramework = {
    createAndInitialize(
      zkUri = zkUri,
      zkCredentials = sys.env.get("ZOOKEEPER_USER").zip(sys.env.get("ZOOKEEPER_SECRET")).headOption
    )
  }

  def createAndInitialize(
    zkUri: ZooKeeperUri,
    zkCredentials: Option[(String, String)]
  ): CuratorFramework = {
    val zkClientBuilder = CuratorFrameworkFactory
      .builder()
      .connectString(zkUri.connectString)
      .retryPolicy(new ExponentialBackoffRetry(baseSleepTimeMs, retries))

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
