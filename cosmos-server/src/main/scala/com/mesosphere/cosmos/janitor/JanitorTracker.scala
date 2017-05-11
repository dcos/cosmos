package com.mesosphere.cosmos.janitor

import java.net.InetAddress
import java.nio.file.Paths

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import org.apache.commons.lang.SerializationUtils
import org.apache.curator.framework.CuratorFramework
import com.mesosphere.cosmos.janitor.SdkJanitor._
import org.slf4j.Logger

trait Tracker {
  def createZkRecord(appId: AppId): Unit
  def deleteZkRecord(appId: AppId): Unit
  def failZkRecord(appId: AppId): Unit
  def getZkRecord(appId: AppId): UninstallStatus
}

/** Tracks work being done by the Janitor */
final class JanitorTracker(
  curator: CuratorFramework
) extends Tracker {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  private def zkRecordPath(appId: AppId): String = {
    // uninstalls/<name>/host
    // Replace slashes in the host so that it gets stored as a single node.
    Paths.get(UninstallFolder, appId.toString, InetAddress.getLocalHost.toString.replaceAll("/", "")).toString
  }

  override def createZkRecord(appId: AppId): Unit = {
    Option(curator.checkExists().forPath(zkRecordPath(appId))) match {
      case Some(thing) =>
        logger.info("When storing UninstallStatus for {}, found stale status of {} in ZK", appId, getZkRecord(appId))
        val _ = curator.setData().forPath(zkRecordPath(appId), SerializationUtils.serialize(InProgress))
      case _ =>
        val _ = curator.create()
          .creatingParentContainersIfNeeded()
          .forPath(zkRecordPath(appId),
            SerializationUtils.serialize(InProgress))
    }
  }

  override def deleteZkRecord(appId: AppId): Unit = {
    val _ = curator.delete().forPath(zkRecordPath(appId))
  }

  override def failZkRecord(appId: AppId): Unit = {
    val _ = curator.setData()
      .forPath(zkRecordPath(appId),
        SerializationUtils.serialize(Failed))
  }

  override def getZkRecord(appId: AppId): UninstallStatus = {
    SerializationUtils.deserialize(curator.getData.forPath(zkRecordPath(appId))) match {
      case status: UninstallStatus => status
      case _ => throw new Exception("Unable to deserialize UninstallStatus for: %s".format(appId))
    }
  }


}
