package com.mesosphere.cosmos.janitor

import java.net.InetAddress
import java.nio.file.Paths

import com.mesosphere.cosmos.janitor.SdkJanitor._
import com.mesosphere.cosmos.model.StorageEnvelope
import com.mesosphere.cosmos.storage.v1.model.Failed
import com.mesosphere.cosmos.storage.v1.model.InProgress
import com.mesosphere.cosmos.storage.v1.model.UninstallStatus
import com.mesosphere.cosmos.storage.v1.model.UninstallStatus._
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import org.apache.curator.framework.CuratorFramework
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

  private def setZkData(appId: AppId, status: UninstallStatus): Unit = {
    val _ = curator.setData()
      .forPath(zkRecordPath(appId), StorageEnvelope.encodeData(status))
  }

  private def createZkData(appId: AppId, status: UninstallStatus): Unit = {
    val _ = curator.create()
      .creatingParentContainersIfNeeded()
      .forPath(zkRecordPath(appId),
        StorageEnvelope.encodeData(status))
  }

  override def createZkRecord(appId: AppId): Unit = {
    Option(curator.checkExists().forPath(zkRecordPath(appId))) match {
      case Some(thing) =>
        logger.info("When storing UninstallStatus for {}, found stale status of {} in ZK", appId, getZkRecord(appId))
        setZkData(appId, InProgress)
      case _ =>
        createZkData(appId, InProgress)
    }
  }

  override def deleteZkRecord(appId: AppId): Unit = {
    val _ = curator.delete().forPath(zkRecordPath(appId))
  }

  override def failZkRecord(appId: AppId): Unit = {
    setZkData(appId, Failed)
  }

  override def getZkRecord(appId: AppId): UninstallStatus = {
    StorageEnvelope.decodeData[UninstallStatus](curator.getData.forPath(zkRecordPath(appId)))
  }
}
